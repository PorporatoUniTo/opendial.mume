@if "%DEBUG%" == "" @echo off
set JAVA_OPTS=%* 
@rem ##########################################################################
@rem
@rem  opendial startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Add default JVM options here. You can also use JAVA_OPTS and OPENDIAL_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windows variants

if not "%OS%" == "Windows_NT" goto win9xME_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\opendial-1.4.jar;%APP_HOME%\lib\tint-runner-0.2.jar;%APP_HOME%\lib\tint-digimorph-annotator-0.2.jar;%APP_HOME%\lib\fcw-udpipe-api-0.1.1.jar;%APP_HOME%\lib\fcw-utils-0.1.1.jar;%APP_HOME%\lib\httpclient-4.5.1.jar;%APP_HOME%\lib\balloontip-1.2.4.1.jar;%APP_HOME%\lib\org.json-2.0.jar;%APP_HOME%\lib\jfreechart-1.0.19.jar;%APP_HOME%\lib\jung-graph-impl-2.0.1.jar;%APP_HOME%\lib\jung-visualization-2.0.1.jar;%APP_HOME%\lib\exp4j.jar;%APP_HOME%\lib\google-maps-services-0.9.0.jar;%APP_HOME%\lib\slf4j-simple-1.7.25.jar;%APP_HOME%\lib\tint-geoloc-annotator-0.1.jar;%APP_HOME%\lib\httpcore-4.4.3.jar;%APP_HOME%\lib\commons-logging-1.2.jar;%APP_HOME%\lib\commons-codec-1.9.jar;%APP_HOME%\lib\jcommon-1.0.23.jar;%APP_HOME%\lib\jung-algorithms-2.0.1.jar;%APP_HOME%\lib\jung-api-2.0.1.jar;%APP_HOME%\lib\collections-generic-4.01.jar;%APP_HOME%\lib\okhttp-3.11.0.jar;%APP_HOME%\lib\tint-tokenizer-0.2.jar;%APP_HOME%\lib\tint-readability-0.2.jar;%APP_HOME%\lib\tint-verb-0.2.jar;%APP_HOME%\lib\utils-corenlp-3.1.1.jar;%APP_HOME%\lib\fcw-linking-0.1.1.jar;%APP_HOME%\lib\tint-heideltime-annotator-0.2.jar;%APP_HOME%\lib\utils-gson-3.1.1.jar;%APP_HOME%\lib\gson-2.8.5.jar;%APP_HOME%\lib\tint-digimorph-0.2.jar;%APP_HOME%\lib\utils-core-3.1.1.jar;%APP_HOME%\lib\fcw-depparse-0.1.1.jar;%APP_HOME%\lib\stanford-corenlp-3.8.0.jar;%APP_HOME%\lib\log4j-slf4j-impl-2.6.1.jar;%APP_HOME%\lib\hyph-7.0.1.jar;%APP_HOME%\lib\layout-7.0.1.jar;%APP_HOME%\lib\kernel-7.0.1.jar;%APP_HOME%\lib\io-7.0.1.jar;%APP_HOME%\lib\slf4j-api-1.7.25.jar;%APP_HOME%\lib\log4j-core-2.6.1.jar;%APP_HOME%\lib\log4j-api-2.6.1.jar;%APP_HOME%\lib\jsr305-3.0.0.jar;%APP_HOME%\lib\mapdb-3.0.1.jar;%APP_HOME%\lib\guava-20.0-rc1.jar;%APP_HOME%\lib\grizzly-http-server-2.3.21.jar;%APP_HOME%\lib\grizzly-http-2.3.21.jar;%APP_HOME%\lib\grizzly-framework-2.3.21.jar;%APP_HOME%\lib\tint-models-0.2.jar;%APP_HOME%\lib\colt-1.2.0.jar;%APP_HOME%\lib\okio-1.14.0.jar;%APP_HOME%\lib\commons-cli-1.3.1.jar;%APP_HOME%\lib\AppleJavaExtensions-1.4.jar;%APP_HOME%\lib\jollyday-0.4.9.jar;%APP_HOME%\lib\commons-lang3-3.3.1.jar;%APP_HOME%\lib\lucene-queryparser-4.10.3.jar;%APP_HOME%\lib\lucene-analyzers-common-4.10.3.jar;%APP_HOME%\lib\lucene-queries-4.10.3.jar;%APP_HOME%\lib\lucene-core-4.10.3.jar;%APP_HOME%\lib\javax.servlet-api-3.0.1.jar;%APP_HOME%\lib\xom-1.2.10.jar;%APP_HOME%\lib\joda-time-2.9.4.jar;%APP_HOME%\lib\ejml-0.23.jar;%APP_HOME%\lib\javax.json-1.0.4.jar;%APP_HOME%\lib\protobuf-java-3.2.0.jar;%APP_HOME%\lib\jackson-mapper-asl-1.9.12.jar;%APP_HOME%\lib\ahocorasick-0.3.0.jar;%APP_HOME%\lib\commons-lang-2.6.jar;%APP_HOME%\lib\xercesImpl-2.8.0.jar;%APP_HOME%\lib\xalan-2.7.0.jar;%APP_HOME%\lib\concurrent-trees-2.6.1.jar;%APP_HOME%\lib\heideltime-2.2.1.jar;%APP_HOME%\lib\uimaj-core-2.8.1.jar;%APP_HOME%\lib\concurrent-1.3.4.jar;%APP_HOME%\lib\jaxb-api-2.2.7.jar;%APP_HOME%\lib\lucene-sandbox-4.10.3.jar;%APP_HOME%\lib\jackson-core-asl-1.9.12.jar;%APP_HOME%\lib\commons-csv-1.2.jar;%APP_HOME%\lib\kotlin-stdlib-1.0.2.jar;%APP_HOME%\lib\eclipse-collections-forkjoin-7.1.2.jar;%APP_HOME%\lib\eclipse-collections-7.1.2.jar;%APP_HOME%\lib\eclipse-collections-api-7.1.2.jar;%APP_HOME%\lib\lz4-1.3.0.jar;%APP_HOME%\lib\elsa-3.0.0-M5.jar;%APP_HOME%\lib\kotlin-runtime-1.0.2.jar;%APP_HOME%\lib\jcip-annotations-1.0.jar

@rem Execute opendial
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %OPENDIAL_OPTS%  -classpath "%CLASSPATH%" opendial.DialogueSystem %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable OPENDIAL_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%OPENDIAL_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
