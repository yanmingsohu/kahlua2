# Lua on Java

This is an ancient engine from 10 years ago, why should I rebuild it?
Because Proje Zombie uses it as a scripting engine and I love the game,
But it is obvious that the game has been dragged down by this engine. If many functions are made more complicated, the game frame rate will be negatively affected.
The PZ game team obviously doesn't have time to update the engine, they have more important work to do,
So I will try to rewrite the core functionality in java bytecode to make the engine run faster,
God bless me!


## My goals are twofold

1. This project is a GameBody simulator: https://github.com/yanmingsohu/zPlayableGameBoy
   It's impossible to run it now, you can only get a frame rate of a few seconds, if I can increase it to 10 frames/second, I will achieve the first goal.

2. If goal 1 is completed, I will make a PZ compatibility layer, because the game has deeply embedded this engine, and I want to ensure that it can be ported to the game with minimal changes.



# Performance

This is the speed of running the game with the original version of the virtual machine,
You can see that the average frame rate is 1 frame/second, and it is very unstable.

![screen1](https://github.com/yanmingsohu/kahlua2/blob/master/screen1.png)

Now let's working ...



# Progress

1. [x] Understand how the program works
1. [x] Understand JVM byte code
1. [x] Understand Lua byte code
1. [ ] Replace interpreter with java code compiler
1. [ ] Explore java asm best practices


# About

* [bit](https://github.com/AlberTajuelo/bitop-lua) This is a library that uses lua to simulate bit operations. I have implemented it in Kahlua using java, so this library is only used as a reference.
* [LuaGB](https://github.com/zeta0134/LuaGB) This is the GameBoy emulator, it's made in Lua
* [jvm](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html)
* [Lua byte code](https://the-ravi-programming-language.readthedocs.io/en/latest/lua_bytecode_reference.html)


# Support

If you think my work is good, you can support me here: https://www.patreon.com/messycode411