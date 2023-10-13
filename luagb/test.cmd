@echo off

set cp=D:\game-tool\kahlua2\testsuite\bin\classes\;.;
set cp=%cp%;D:\game-tool\kahlua2\bin\classes\core\
set cp=%cp%;D:\game-tool\kahlua2\bin\classes\interpreter\
set cp=%cp%;D:\game-tool\kahlua2\bin\classes\j2se\

set help=D:\game-tool\kahlua2\testsuite\lua

javac -cp %cp% Luaenv.java
java -cp %cp% Luaenv 