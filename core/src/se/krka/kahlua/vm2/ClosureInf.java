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


public class ClosureInf {

  public final Prototype prototype;
  public final UpValue[] upvalues;
  public final int arrIndex;
  public final int stackIndex;
  public final String funcName;

  private Method mc;
  private LuaCallFrame oframe;
  private LuaClosure ocl;


  public ClosureInf(Prototype prototype,
                    int arrIndex,
                    String funcName,
                    int stackIndex) {
    this.prototype = prototype;
    this.upvalues = new UpValue[prototype.numUpvalues];
    this.arrIndex = arrIndex;
    this.funcName = funcName;
    this.stackIndex = stackIndex;
  }


  public void installMethod(LuaScript ls) {
    try {
      mc = ls.getClass().getDeclaredMethod(funcName);

    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }


  public void call(LuaScript ls) {
    try {
      mc.invoke(ls);

    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }


  public void call(JavaFunction f) {
    int nReturnValues = f.call(oframe, oframe.nArguments);

    int top = oframe.getTop();
    int actualReturnBase = top - nReturnValues;

    int diff = oframe.returnBase - oframe.localBase;
    oframe.stackCopy(actualReturnBase, diff, nReturnValues);
    oframe.setTop(nReturnValues + diff);
  }


  public void newFrame(Coroutine c, int lcBase, int rBase, int nArg, boolean isLua) {
    LuaClosure lc = new LuaClosure(prototype, c.environment);
    LuaCallFrame cf = c.pushNewCallFrame(lc, null, lcBase, rBase, nArg, isLua, false);
    cf.init();

    this.oframe = cf;
    this.ocl = lc;
  }


  public LuaCallFrame getOldFrame() {
    return oframe;
  }


  public LuaClosure getOldClosure() {
    return ocl;
  }
}
