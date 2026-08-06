@echo off
setlocal EnableExtensions

rem Resolve the repository root from this script location.
for %%I in ("%~dp0..\..\..\..") do set "ROOT_DIR=%%~fI"
if not defined ROOT_DIR goto :fail
if not exist "%ROOT_DIR%\genie-backend\pom.xml" goto :fail

rem Section 18 frozen C-only test inventory.
set "TESTS=Phase2RequestValidatorTest,GptProcessV1RegressionTest,Phase2ConversationLifecycleTest,DirectReactAdapterTest,DirectPlanSolveAdapterTest,RouterFallbackTest,OrchestrationValidatorTest,SerialMaxConcurrencyTest,InputRefsTransferTest,StepFailureSkipTest,ReplanLimitTest,ResultReuseSignatureTest,ConfiguredReactAgentFactoryTest,AgentTaskResultParserTest,SummaryFallbackTest,Phase2TerminalRaceTest,OrchestrationSnapshotPrunerTest,FinalAnswerPersistenceTest,ConfiguredAgentTestControllerTest,Phase2FakePortContractTest"

if /I "%~1"=="--full" (
  set "TESTS="
  set "MVN_TEST_ARG="
) else (
  set "MVN_TEST_ARG=-Dtest=%TESTS%"
)

echo ^>^>^> Phase2-C independent acceptance (C-G01..C-G12)
if defined TESTS echo ^>^>^> Docker Maven tests: %TESTS%
if not defined TESTS echo ^>^>^> Docker Maven tests: full backend regression

docker volume inspect genie-maven-cache >nul 2>nul || docker volume create genie-maven-cache >nul
if errorlevel 1 goto :fail

docker run --rm --name genie-phase2c-acceptance -v "%ROOT_DIR%:/workspace" -v "genie-maven-cache:/root/.m2" -v "//var/run/docker.sock:/var/run/docker.sock" -w "/workspace/genie-backend" -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --add-host=host.docker.internal:host-gateway maven:3.9.9-eclipse-temurin-17 mvn -Djacoco.skip=true %MVN_TEST_ARG% test
if errorlevel 1 goto :fail

echo Overall: PASS
exit /b 0

:fail
echo Overall: FAIL
exit /b 1
