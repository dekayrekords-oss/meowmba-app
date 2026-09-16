"""Optional libebur128 measurements; missing libraries never produce estimates.

ABI: https://github.com/jiixyj/libebur128/blob/v1.2.6/ebur128/ebur128.h
Only mono/stereo layouts are claimed; multichannel needs an explicit channel map.
"""
import array
import ctypes as C
import math
from pathlib import Path


class LoudnessMeter:
    def __init__(self, channels, rate):
        self.channels=channels; self.rate=rate; self.state=None; self.lib=None
        self.reason='Install libebur128 on the Mac for integrated loudness and true-peak measurement.'
        if channels not in (1,2):
            self.reason='Loudness measurement requires an explicit channel map for multichannel audio.'
            return
        for path in ('/opt/homebrew/opt/libebur128/lib/libebur128.dylib', '/usr/local/opt/libebur128/lib/libebur128.dylib'):
            if not Path(path).is_file(): continue
            try: self.lib=C.CDLL(path); break
            except OSError: pass
        if self.lib is None: return
        lib=self.lib
        for name,ret,args in [
            ('ebur128_init',C.c_void_p,[C.c_uint,C.c_ulong,C.c_int]),
            ('ebur128_destroy',None,[C.POINTER(C.c_void_p)]),
            ('ebur128_get_version',None,[C.POINTER(C.c_int)]*3),
            ('ebur128_add_frames_double',C.c_int,[C.c_void_p,C.POINTER(C.c_double),C.c_size_t]),
            ('ebur128_loudness_global',C.c_int,[C.c_void_p,C.POINTER(C.c_double)]),
            ('ebur128_loudness_range',C.c_int,[C.c_void_p,C.POINTER(C.c_double)]),
            ('ebur128_true_peak',C.c_int,[C.c_void_p,C.c_uint,C.POINTER(C.c_double)]),
        ]:
            fn=getattr(lib,name);fn.restype=ret;fn.argtypes=args
        version=[C.c_int() for _ in range(3)];lib.ebur128_get_version(*(C.byref(v) for v in version))
        self.version='.'.join(str(v.value) for v in version)
        self.state=lib.ebur128_init(channels,rate,63) # I | LRA | TRUE_PEAK, including required modes.
        if not self.state: raise ValueError('Loudness meter could not allocate its bounded state')
        self.frames=0

    def __enter__(self): return self
    def __exit__(self,*_):
        if self.state:
            state=C.c_void_p(self.state);self.lib.ebur128_destroy(C.byref(state));self.state=None

    def add(self, values, scale):
        if not self.state:return
        normalized=array.array('d',(v/scale for v in values))
        if len(normalized)%self.channels:raise ValueError('Incomplete loudness frames')
        ptr=(C.c_double*len(normalized)).from_buffer(normalized)
        count=len(normalized)//self.channels
        if self.lib.ebur128_add_frames_double(self.state,ptr,count):raise ValueError('Loudness meter rejected audio frames')
        self.frames+=count

    def finish(self):
        if not self.state:return dict(available=False,reason=self.reason)
        loudness=C.c_double();lra=C.c_double()
        if self.lib.ebur128_loudness_global(self.state,C.byref(loudness)) or self.lib.ebur128_loudness_range(self.state,C.byref(lra)):
            raise ValueError('Loudness measurement failed')
        # Capture programme loudness before flushing the FIR tail. Padding is
        # solely for final inter-sample peaks, never added to duration or LUFS.
        zeros=(C.c_double*(128*self.channels))()
        if self.lib.ebur128_add_frames_double(self.state,zeros,128):raise ValueError('True-peak filter flush failed')
        peaks=[]
        for channel in range(self.channels):
            peak=C.c_double()
            if self.lib.ebur128_true_peak(self.state,channel,C.byref(peak)) or not math.isfinite(peak.value):
                raise ValueError('True-peak measurement failed')
            peaks.append(20*math.log10(peak.value) if peak.value>0 else None)
        return dict(available=True,engine='libebur128',version=self.version,
                    integrated_lufs=loudness.value if math.isfinite(loudness.value) else None,
                    loudness_range_lu=lra.value if self.frames/self.rate>=3 and math.isfinite(lra.value) else None,
                    true_peak_dbtp=peaks,channel_layout='mono' if self.channels==1 else 'stereo',
                    note='Silence/insufficient programme can have no integrated LUFS. True-peak interpolation depends on library version.')
