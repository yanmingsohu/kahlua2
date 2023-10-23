/*
 Copyright (c) 2023 Yanming <yanmingsohu@gmail.com>

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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class ClosureInf /*implements JavaFunction */{

  public final Prototype prototype;
  public final UpValue[] upvalues;
  public final int arrIndex;
  public final int stackIndex;
  public final String funcName;
  public final String luaName;

  private Method mc;
  private LuaCallFrame oframe;
  private LuaClosure ocl;
  private LuaScript bind;

  private int localBase;
  private int returnBase;
  private int nArguments;
  private int top;


  public ClosureInf(Prototype prototype,
                    int arrIndex,
                    String funcName,
                    int stackIndex,
                    String luaName)
  {
    this.prototype = prototype;
    this.upvalues = new UpValue[prototype.numUpvalues];
    this.arrIndex = arrIndex;
    this.funcName = funcName;
    this.stackIndex = stackIndex;
    this.luaName = luaName;
  }


  public void installMethod(LuaScript ls) {
    try {
      mc = ls.getClass().getDeclaredMethod(funcName);
      bind = ls;

    } catch (NoSuchMethodException e) {
      throw new LuaFail(e);
    }
  }


  public void call(LuaScript ls) {
    try {
      mc.invoke(ls);

    } catch (Throwable e) {
      throw new LuaFail(e.getCause());
    }
  }


  public void call() {
    call(bind);
  }


  /**
   * Call from older Thread object
   * mock javafunction (TODO: remove)
   */
  public int call(LuaCallFrame callFrame, int nArguments) {
    this.nArguments = nArguments;
    this.oframe = callFrame;
    call();
    return 0;
  }


  public void newFrame(Coroutine c) {
    ComputStack cs = new ComputStack(top, nArguments, localBase, returnBase);

    this.oframe = cs.pushFrame(c, prototype);
    this.ocl = oframe.closure;
    this.ocl.upvalues = this.upvalues;

    this.oframe.init();

    //TODO: remove this code
//    this.oframe.setTop(prototype.maxStacksize);
    //    for (int i=0; i<upvalues.length; ++i) {
    //      this.ocl.upvalues[i] = this.upvalues[i];
    //    }
    //    Tool.pl("(0==", cs, ')');
  }


  public void frameParams(int lcBase, int rBase, int nArg) {
    this.localBase = lcBase;
    this.returnBase = rBase;
    this.nArguments = nArg;
    this.top = -1;
  }


  public void frameParams(ComputStack cs) {
    this.localBase = cs.localBase;
    this.returnBase = cs.returnBase;
    this.nArguments = cs.nArguments;
    this.top = cs.top;
  }


  public void setFrame(LuaClosure lc, LuaCallFrame cf) {
    this.oframe = cf;
    this.ocl = lc;
  }


  public void restoreStack(LuaCallFrame cf) {
    if (cf.restoreTop) {
      cf.setTop(prototype.maxStacksize);
    }
  }


  public LuaCallFrame getOldFrame() {
//    Tool.pl("(2==", oframe.localBase, oframe.returnBase, oframe.nArguments, oframe.hashCode(), oframe.getTop(), ')');
    return oframe;
  }


  public LuaClosure getOldClosure() {
    return ocl;
  }


  public String toString() {
    return "ClosureInf@"+ Tool.hash(this) +
      " "+ funcName +" "+ luaName +
      " "+ prototype;
  }

}
