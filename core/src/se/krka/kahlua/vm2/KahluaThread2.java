/*
 Copyright (c) 2023 Kristofer Karlsson <kristofer.karlsson@gmail.com>

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */

package se.krka.kahlua.vm2;

import se.krka.kahlua.vm.*;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;


/**
 * KahluaThread is modified into an inheritable class,
 * And the `luaMainloop` method is changed to protected method without final.
 */
public class KahluaThread2 extends KahluaThread {

  private String outputDir;
  final Platform platform;
  public boolean debug;

  final static String[] opNames = {
    /*  0 */  "OP_MOVE"
    /*  1 */ ,"OP_LOADK"
    /*  2 */ ,"OP_LOADBOOL"
    /*  3 */ ,"OP_LOADNIL"
    /*  4 */ ,"OP_GETUPVAL"
    /*  5 */ ,"OP_GETGLOBAL"

    /*  6 */ ,"OP_GETTABLE"
    /*  7 */ ,"OP_SETGLOBAL"
    /*  8 */ ,"OP_SETUPVAL"
    /*  9 */ ,"OP_SETTABLE"
    /* 10 */ ,"OP_NEWTABLE"

    /* 11 */ ,"OP_SELF"
    /* 12 */ ,"OP_ADD"
    /* 13 */ ,"OP_SUB"
    /* 14 */ ,"OP_MUL"
    /* 15 */ ,"OP_DIV"

    /* 16 */ ,"OP_MOD"
    /* 17 */ ,"OP_POW"
    /* 18 */ ,"OP_UNM"
    /* 19 */ ,"OP_NOT"
    /* 20 */ ,"OP_LEN"

    /* 21 */ ,"OP_CONCAT"
    /* 22 */ ,"OP_JMP"
    /* 23 */ ,"OP_EQ"
    /* 24 */ ,"OP_LT"
    /* 25 */ ,"OP_LE"

    /* 26 */ ,"OP_TEST"
    /* 27 */ ,"OP_TESTSET"
    /* 28 */ ,"OP_CALL"
    /* 29 */ ,"OP_TAILCALL"
    /* 30 */ ,"OP_RETURN"

    /* 31 */ ,"OP_FORLOOP"
    /* 32 */ ,"OP_FORPREP"
    /* 33 */ ,"OP_TFORLOOP"
    /* 34 */ ,"OP_SETLIST"
    /* 35 */ ,"OP_CLOSE"

    /* 36 */ ,"OP_CLOSURE"
    /* 37 */ ,"OP_VARARG"
  };



  public KahluaThread2(Platform platform, KahluaTable environment) {
    this(System.out, platform, environment);
  }


  public KahluaThread2(PrintStream s, Platform p, KahluaTable e) {
    super(s, p, e);
    this.platform = p;
  }


  public int call(int nArguments) {
    int top = currentCoroutine.getTop();
    int base = top - nArguments - 1;
    Object o = currentCoroutine.objectStack[base];

    if (o == null) {
      throw new RuntimeException("tried to call nil");
    }

    if (o instanceof JavaFunction) {
      return callJava((JavaFunction) o, base + 1, base, nArguments);
    }

    if (o instanceof ClosureInf) {
      ClosureInf ci = (ClosureInf)o;
      LuaCallFrame f = currentCoroutine.currentCallFrame();
      if (f != null) {
        ci.setFrame(f.closure, f);
      } else {
        ci.frameParams(base + 1, base, nArguments, true);
      }
      ci.call();
      return currentCoroutine.getTop() - base;
    }

    if (!(o instanceof LuaClosure)) {
      throw new RuntimeException("tried to call a non-function");
    }

    try {
      LuaClosure lc = (LuaClosure) o;
      LuaBuilder luab = new LuaBuilder(lc.prototype.name, outputDir);
      luab.debug = debug;
      luab.makeJavacode(lc.prototype);

      LuaScript x = luab.createJavaAgent();
      x.reinit(this, currentCoroutine);
      x.run();

    } catch (NoSuchMethodException | InstantiationException
          | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }

    int nReturnValues = currentCoroutine.getTop() - base;
    currentCoroutine.stackTrace = "";
    return nReturnValues;
  }


  public int call(LuaClosure oldc, Coroutine cor, int nArguments) {
    currentCoroutine = cor;
    int top = cor.getTop();
    int base = top - nArguments - 1;

    LuaCallFrame callFrame = currentCoroutine.pushNewCallFrame(oldc, null,
      base + 1, base, nArguments, false, false);
    callFrame.init();

    luaMainloop();

    int nReturnValues = currentCoroutine.getTop() - base;
    currentCoroutine.stackTrace = "";
    currentCoroutine.popCallFrame();

    return nReturnValues;
  }


  public static String metaOpName(int i) {
    return meta_ops[i];
  }


  public static int opNamesLen() {
    return opNames.length;
  }


  public static String opName(int i) {
    return opNames[i];
  }


  public void setOutputDir(String dir) {
    this.outputDir = dir;
  }
}
