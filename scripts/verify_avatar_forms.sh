#!/usr/bin/env bash
# Build 119's acceptance set. This runs JVM/native-render tests, not an APK install.
# Supply host-specific Gradle options as arguments; keep credentials out of this file.
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
suites=(
  GeneratedAvatar\* AvatarForm\* AgentCancellationLifecycleTest
  OrchestratorTest AgentRouterTest AgentViewModelTest AgentReliabilityTest
  CrossDeviceControlTest ProjectExecutionContextTest ToolCallBrainTest
  OfficeHelpers\* FruitFlyAvatarTest AvatarMorph\* LumenIntegrationTest
  LumenNaturalMotionTest CustomAnimationTest InventedSceneRenderTest
  CompanionBrainControlsTest BrainAvatarVisibilityTest CameraPrivacyViewModelTest
  VisionPipelineTest VisionManagerLifecycleTest
  neural.MaleCnsServerManagerTest neural.NeuralServerReplacementTest
)
args=(:app:testDebugUnitTest --console=plain)
for suite in "${suites[@]}"; do
  args+=(--tests "com.example.meowmba.$suite")
done
exec ./gradlew "${args[@]}" "$@"
