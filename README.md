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
You can see that the average frame rate is 1~4 frame/second, and it is very unstable.

![screen1](https://github.com/yanmingsohu/kahlua2/blob/master/screen1.png)


When I changed the interpreted mode to compiled mode, the performance did not improve significantly. It seems that the optimization ability of jvm is very strong. Now the performance bottleneck is on KahluaTable.

If I couldn't find something that worked, I'd stop here, it looks like the attempt at performance improvement failed.

![screen2](https://github.com/yanmingsohu/kahlua2/blob/master/screen2.png)
```
Special instructions in pictures
N: Table created completely from scratch
R: Recycling Table
D: Table discarded because it was released too quickly and too late to be processed.
```

The latest version can be merged into the old code, because there is no change in the logic of the old code. To try the new version, you need to switch KahluaThread to KahluaThread2 and J2SEPlatform to J2SEPlatform2 (optional)

I added a J2SEPlatform2 and allocated a new KahluaTableImpl2 object in it. This object is faster when the user only uses the Table as an array, and the array/Map inside the Table will be recycled. You can pass J2SEPlatform2.setMemoryManager() to set up a memory manager, For example, every 10 minutes, release half of the memory.

## Benchmarks

This is the result of running Lua Benchmarks:

KahluaThread2 represents a new compiler, which has obvious advantages in long-term testing

```
0^ makeJavacode(LuaBuilder.java:  79) -  Build ./Lua-Benchmarks/mandel.lua used 63 ms
1^ t(Benchmarks.java:  77) -  Benchmarks >> mandel
 KahluaThread2 	Used 218 ms
 KahluaThread 	Used 100 ms

2^ makeJavacode(LuaBuilder.java:  79) -  Build ./Lua-Benchmarks/fixpoint-fact.lua used 9 ms
3^ t(Benchmarks.java:  77) -  Benchmarks >> fixpoint-fact
 KahluaThread2 	Used 9 ms
 KahluaThread 	Used 0 ms

4^ makeJavacode(LuaBuilder.java:  79) -  Build ./Lua-Benchmarks/binary-trees.lua used 16 ms
5^ t(Benchmarks.java:  77) -  Benchmarks >> binary-trees
 KahluaThread2 	Used 530 ms
 KahluaThread 	Used 1046 ms

6^ makeJavacode(LuaBuilder.java:  79) -  Build ./Lua-Benchmarks/fannkuch-redux.lua used 0 ms
7^ t(Benchmarks.java:  77) -  Benchmarks >> fannkuch-redux
 KahluaThread2 	Used 5634 ms
 KahluaThread 	Used 22883 ms

8^ makeJavacode(LuaBuilder.java:  79) -  Build ./Lua-Benchmarks/heapsort.lua used 0 ms
9^ t(Benchmarks.java:  77) -  Benchmarks >> heapsort
 KahluaThread2 	Used 604 ms
 KahluaThread 	Used 1574 ms

10^ makeJavacode(LuaBuilder.java:  79) -  Build ./Lua-Benchmarks/qt.lua used 47 ms
11^ t(Benchmarks.java:  77) -  Benchmarks >> qt
 KahluaThread2 	Used 6746 ms
 KahluaThread 	Used 14552 ms

12^ makeJavacode(LuaBuilder.java:  79) -  Build ./Lua-Benchmarks/n-body.lua used 16 ms
13^ t(Benchmarks.java:  77) -  Benchmarks >> n-body
 KahluaThread2 	Used 47 ms
 KahluaThread 	Used 0 ms

14^ makeJavacode(LuaBuilder.java:  79) -  Build ./Lua-Benchmarks/queen.lua used 0 ms
15^ t(Benchmarks.java:  77) -  Benchmarks >> queen
 KahluaThread2 	Used 375 ms
 KahluaThread 	Used 767 ms

16^ makeJavacode(LuaBuilder.java:  79) -  Build ./Lua-Benchmarks/sieve.lua used 16 ms
17^ t(Benchmarks.java:  77) -  Benchmarks >> sieve
 KahluaThread2 	Used 109 ms
 KahluaThread 	Used 172 ms

18^ makeJavacode(LuaBuilder.java:  79) -  Build ./Lua-Benchmarks/spectral-norm.lua used 0 ms
19^ t(Benchmarks.java:  77) -  Benchmarks >> spectral-norm
 KahluaThread2 	Used 16 ms
 KahluaThread 	Used 16 ms
```

## Other

Call KahluaThread2.printStatistics() Can count the usage of Lua instructions.

`se.krka.kahlua.j2se.LuaRuntime` is a small runtime,
inheriting it can quickly start a Lua script with java code.


# Progress

1. [x] Understand how the program works
1. [x] Understand JVM byte code
1. [x] Understand Lua byte code
1. [x] Replace interpreter with java code compiler
1. [x] Explore java asm best practices
1. [x] Optimize Table objects
1. [ ] Find new ways to optimize performance


# About

* [bit](https://github.com/AlberTajuelo/bitop-lua) This is a library that uses lua to simulate bit operations. I have implemented it in Kahlua using java, so this library is only used as a reference.
* [LuaGB](https://github.com/zeta0134/LuaGB) This is the GameBoy emulator, it's made in Lua
* [jvm](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html)
* [Lua byte code](https://the-ravi-programming-language.readthedocs.io/en/latest/lua_bytecode_reference.html)


# Support

If you think my work is good, you can support me here: https://www.patreon.com/messycode411