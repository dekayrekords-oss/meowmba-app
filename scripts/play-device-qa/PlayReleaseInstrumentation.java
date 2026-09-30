package com.meow.qa.release;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.ContentValues;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.net.Uri;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.accessibilityservice.AccessibilityServiceInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** SDK-only checks against the actual minified APK, on a disposable emulator only.
 * AndroidX's runner references shared Kotlin/AndroidX APIs removed by release R8.
 * This harness neither changes production keep rules nor depends on those APIs. */
public class PlayReleaseInstrumentation extends Instrumentation {
    private Bundle arguments;
    private Context context;
    private File evidence;

    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments=args; start(); }

    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            check("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE), "Disposable Android emulator required");
            context=getTargetContext();
            AccessibilityServiceInfo automation=getUiAutomation().getServiceInfo();
            automation.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            getUiAutomation().setServiceInfo(automation);
            evidence=new File(context.getExternalFilesDir(null), "play191-qa"); evidence.mkdirs();
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            check(launch!=null, "App launcher missing"); launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(launch);
            File database=context.getDatabasePath("meowmba_database");
            await(() -> database.exists(), "App database creation");
            String phase=arguments.getString("phase", "verify");
            if ("seed".equals(phase)) {
                check(versionCode()==152, "Seed only the QA-resigned reviewed beta152");
                context.getSharedPreferences("qa191_upgrade", 0).edit().putString("sentinel", "preserve191").commit();
                File marker=new File(context.getFilesDir(), "memba_workspace/QA191-upgrade.txt"); marker.getParentFile().mkdirs();
                Files.write(marker.toPath(), "preserve191".getBytes(StandardCharsets.UTF_8));
                try(SQLiteDatabase db=SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS qa191_upgrade_fixture(id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
                    db.execSQL("INSERT OR REPLACE INTO qa191_upgrade_fixture VALUES(1, 'preserve191')");
                    db.execSQL("INSERT OR REPLACE INTO memories(id,content,timestamp,type,importance,tags,embedding,sourceMessageId,lastAccessedAt) VALUES(949191,?,?,'SEMANTIC',1,'qa191',NULL,NULL,?)",
                        new Object[]{"QA191 memory should survive an update.",System.currentTimeMillis(),System.currentTimeMillis()});
                    write("old-schema.txt", Integer.toString(db.getVersion()));
                }
                screenshot("old-startup.png");
                result.putString("stream", "PASS: disposable beta152 fixtures seeded\n");
            } else {
                check(versionCode()==191, "Expected release191");
                check("preserve191".equals(context.getSharedPreferences("qa191_upgrade", 0).getString("sentinel", null)), "Preference retained");
                check("preserve191".equals(new String(Files.readAllBytes(new File(context.getFilesDir(), "memba_workspace/QA191-upgrade.txt").toPath()), StandardCharsets.UTF_8)), "Local file retained");
                await(() -> schema(database)==15, "Real Room upgrade to15");
                try(SQLiteDatabase db=SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                    android.database.Cursor c=db.rawQuery("SELECT value FROM qa191_upgrade_fixture WHERE id=1", null)) {
                    check(c.moveToFirst() && "preserve191".equals(c.getString(0)), "SQL fixture retained");
                }
                try(SQLiteDatabase db=SQLiteDatabase.openDatabase(database.getPath(),null,SQLiteDatabase.OPEN_READONLY);
                    android.database.Cursor c=db.rawQuery("SELECT content,tags FROM memories WHERE id=949191",null)) {
                    check(c.moveToFirst() && "QA191 memory should survive an update.".equals(c.getString(0)) && "qa191".equals(c.getString(1)), "Real memory row retained");
                }
                PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS|PackageManager.GET_SERVICES);
                List<String> permissions=info.requestedPermissions==null ? new ArrayList<>() : Arrays.asList(info.requestedPermissions);
                for(String name:new String[]{"READ_MEDIA_IMAGES","READ_MEDIA_VIDEO","READ_MEDIA_AUDIO","READ_MEDIA_VISUAL_USER_SELECTED"}) {
                    check(!permissions.contains("android.permission."+name), "No broad media declaration: "+name);
                    check(context.checkSelfPermission("android.permission."+name)==PackageManager.PERMISSION_DENIED, "No old media grant: "+name);
                }
                if(info.services!=null) for(android.content.pm.ServiceInfo service:info.services)
                    check(!service.name.endsWith("MeowAccessibilityService"), "No Accessibility automation service in Play");
                SystemClock.sleep(1500);
                screenshot("release-startup.png");
                write("startup-ui.txt", describe());
                write("upgrade-result.txt", "PASS:152 to191; Room10 to15; preference, local file, SQL fixture and real memory row retained; no broad media permissions or Accessibility service.\n");
                if ("inspect-files".equals(phase) || "picker".equals(phase)) {
                    if(find("Keep both for now")!=null) { click("Keep both for now"); await(() -> find("Keep both for now")==null,"Edition backup warning acknowledged"); }
                    if(find("Meet your Meow.")!=null) { click("Finish later"); await(() -> find("Make Meow your home screen?")!=null, "Optional launcher step"); }
                    if(find("Make Meow your home screen?")!=null) { click("Not now"); await(() -> find("Make Meow your home screen?")==null, "Launcher declined"); }
                    SystemClock.sleep(1000);
                    if(find("Choose your companion")!=null) {
                        click("Lumen Cat"); scrollTo("Fit to my screen"); click("Fit to my screen"); await(() -> find("Fitted to your screen")!=null,"Avatar fitting");
                    }
                    if(find("Fitted to your screen")!=null) { scrollTo("Keep this size"); click("Keep this size"); await(() -> find("Fitted to your screen")==null,"Size confirmed"); }
                    if(find("Finish later")!=null) { click("Finish later"); await(() -> find("Make yourself at home.")!=null,"Setup ready"); }
                    if(find("Make yourself at home.")!=null) { click("Meet my avatar"); await(() -> find("Make yourself at home.")==null,"Setup complete"); }
                    SystemClock.sleep(1000);
                    if(find("Menu")!=null) { click("Menu"); SystemClock.sleep(500); }
                    screenshot("home.png"); write("home-ui.txt", describe());
                    if("picker".equals(phase)) verifyPhotoImport();
                }
                result.putString("stream", "PASS: SDK release startup, upgrade, data and permission boundaries\n"+describe());
            }
            runOnMainSync(activity::finish);
            finish(Activity.RESULT_OK, result);
        } catch(Throwable error) {
            try { if(evidence!=null) { screenshot("failure.png"); write("failure-ui.txt", describe()); } } catch(Throwable ignored) {}
            result.putString("stream", "FAIL: "+error+"\n"); finish(Activity.RESULT_CANCELED, result);
        }
    }

    private long versionCode() throws Exception { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode(); }
    private void verifyPhotoImport() throws Exception {
        scrollTo("Files"); click("Files"); await(() -> find("Choose phone files")!=null,"Files screen");
        click("Choose phone files"); await(() -> find("Choose photos or videos")!=null,"Play picker controls");
        screenshot("files-picker-controls.png");
        File library=new File(context.getFilesDir(),"memba_workspace");
        Set<String> before=filePaths(library);
        String name="QA191-picker-fixture-"+System.nanoTime()+".png";
        ContentValues values=new ContentValues(); values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/png"); values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MeowQA");
        Uri photo=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        check(photo!=null,"Test-owned photo created");
        try {
            Bitmap bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888); bitmap.eraseColor(0xff000000 | ((int)System.nanoTime() & 0x00ffffff));
            try(java.io.OutputStream out=context.getContentResolver().openOutputStream(photo)) { check(out!=null,"Photo write"); bitmap.compress(Bitmap.CompressFormat.PNG,100,out); }
            bitmap.recycle(); byte[] original;
            try(java.io.InputStream in=context.getContentResolver().openInputStream(photo)) { check(in!=null,"Photo read"); original=readBytes(in); }
            click("Choose photos or videos"); await(() -> !thumbnails().isEmpty(),"Android photo picker");
            screenshot("android-photo-picker.png"); write("photo-picker-ui.txt",describe());
            check(thumbnails().size()==1,"Disposable gallery must contain only the test fixture"); click(thumbnails().get(0));
            await(() -> addButton()!=null,"Selected photo ready"); click(addButton());
            // The system picker may expose an opaque display name. Match a new
            // library file by bytes rather than assuming the original filename.
            await(() -> matchingNewFile(library,before,original)!=null,"Selected photo imported");
            File imported=matchingNewFile(library,before,original); check(Arrays.equals(original,Files.readAllBytes(imported.toPath())),"Imported bytes match selection");
            try(java.io.InputStream in=context.getContentResolver().openInputStream(photo)) { check(Arrays.equals(original,readBytes(in)),"Original bytes unchanged"); }
            await(() -> find("Local files")!=null && find("Choose phone files")!=null,"Returned to Files");
            // Wait for the system sheet's closing animation before the evidence image.
            SystemClock.sleep(700);
            screenshot("selected-photo-imported.png");
            write("picker-result.txt","PASS: actual minified Play APK; Menu to Files; system picker imports only the chosen photo; original bytes unchanged.\n");
        } finally {
            // Only the test-created image in this named disposable emulator.
            context.getContentResolver().delete(photo,null,null);
        }
    }
    private List<AccessibilityNodeInfo> thumbnails() { List<AccessibilityNodeInfo> found=new ArrayList<>(); for(AccessibilityNodeInfo node:nodes()) if(node.getViewIdResourceName()!=null && node.getViewIdResourceName().endsWith("/icon_thumbnail")) found.add(node); return found; }
    private AccessibilityNodeInfo addButton() { for(AccessibilityNodeInfo node:nodes()) if(node.getText()!=null && node.getText().toString().startsWith("Add")) return node; return null; }
    private Set<String> filePaths(File directory) { Set<String> result=new HashSet<>(); File[] entries=directory.listFiles(); if(entries!=null)for(File entry:entries) { if(entry.isFile())result.add(entry.getAbsolutePath()); else if(entry.isDirectory())result.addAll(filePaths(entry)); } return result; }
    private File matchingNewFile(File directory,Set<String> before,byte[] expected) { for(String path:filePaths(directory)) if(!before.contains(path))try { if(Arrays.equals(expected,Files.readAllBytes(new File(path).toPath())))return new File(path); } catch(Exception ignored) {} return null; }
    private byte[] readBytes(java.io.InputStream input) throws Exception { java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int count; while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count); return bytes.toByteArray(); }
    private int schema(File database) { try(SQLiteDatabase db=SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READONLY)) { return db.getVersion(); } }
    private interface Condition { boolean ready(); }
    private void await(Condition condition, String label) { long end=SystemClock.uptimeMillis()+30000; while(!condition.ready() && SystemClock.uptimeMillis()<end) SystemClock.sleep(100); check(condition.ready(), label); }
    private void check(boolean okay, String label) { if(!okay) throw new AssertionError(label); }
    private void write(String name, String value) throws Exception { Files.write(new File(evidence,name).toPath(),value.getBytes(StandardCharsets.UTF_8)); }
    private void screenshot(String name) throws Exception { Bitmap image=getUiAutomation().takeScreenshot(); check(image!=null, "Screenshot available"); try(FileOutputStream out=new FileOutputStream(new File(evidence,name))) { image.compress(Bitmap.CompressFormat.PNG,100,out); } image.recycle(); }
    private List<AccessibilityNodeInfo> nodes() { List<AccessibilityNodeInfo> list=new ArrayList<>(); visit(getUiAutomation().getRootInActiveWindow(),list); return list; }
    private void visit(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list) { if(node==null)return; list.add(node); for(int i=0;i<node.getChildCount();i++)visit(node.getChild(i),list); }
    private AccessibilityNodeInfo find(String label) { for(AccessibilityNodeInfo node:nodes()) if(node.isVisibleToUser() && (label.contentEquals(node.getText()==null ? "" : node.getText()) || label.contentEquals(node.getContentDescription()==null ? "" : node.getContentDescription()))) return node; return null; }
    private void click(String label) { AccessibilityNodeInfo node=find(label); check(node!=null, "Visible control: "+label); click(node); }
    private void click(AccessibilityNodeInfo node) {
        Rect bounds=new Rect(); node.getBoundsInScreen(bounds); check(!bounds.isEmpty(),"Visible tap bounds");
        long at=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0);
        MotionEvent up=MotionEvent.obtain(at,at+50,MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        check(getUiAutomation().injectInputEvent(down,true),"Touch down"); check(getUiAutomation().injectInputEvent(up,true),"Touch up"); down.recycle(); up.recycle();
    }
    private void scrollTo(String label) {
        SystemClock.sleep(500);
        for(int i=0;find(label)==null && i<20;i++) {
            for(AccessibilityNodeInfo node:nodes()) if(node.isScrollable()) {
                Rect bounds=new Rect(); node.getBoundsInScreen(bounds);
                if(bounds.width()>0 && bounds.height()>100) { swipe(bounds.exactCenterX(),bounds.top+bounds.height()*.8f,bounds.exactCenterX(),bounds.top+bounds.height()*.5f); break; }
            }
            SystemClock.sleep(400);
        }
        check(find(label)!=null,"Visible after scrolling: "+label);
    }
    private void swipe(float fromX,float fromY,float toX,float toY) {
        long start=SystemClock.uptimeMillis();
        for(int i=0;i<=8;i++) {
            int action=i==0 ? MotionEvent.ACTION_DOWN : i==8 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            float part=i/8f; MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,fromX+(toX-fromX)*part,fromY+(toY-fromY)*part,0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN); check(getUiAutomation().injectInputEvent(event,true),"Scroll input"); event.recycle(); SystemClock.sleep(35);
        }
    }
    private String describe() { StringBuilder s=new StringBuilder(); for(AccessibilityNodeInfo node:nodes()) { if(node.getText()!=null || node.getContentDescription()!=null) s.append("text=").append(node.getText()).append(" desc=").append(node.getContentDescription()).append(" id=").append(node.getViewIdResourceName()).append(" clickable=").append(node.isClickable()).append('\n'); } return s.toString(); }
}
