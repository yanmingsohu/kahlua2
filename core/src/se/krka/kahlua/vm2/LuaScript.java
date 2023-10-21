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


import se.krka.kahlua.stdlib.BaseLib;
import se.krka.kahlua.vm.*;

import java.util.List;


public abstract class LuaScript implements Runnable {


  protected Platform platform;
  protected KahluaThread2 t;
  protected Coroutine coroutine;
  protected ClosureInf[] plist;


  public LuaScript() {
  }


  public void reinit(KahluaThread2 kt2, Coroutine c, ComputStack cs) {
    this.t = kt2;
    this.coroutine = c;
    this.platform = kt2.platform;
    this.plist[IConst.rootClosure].frameParams(cs);
  }


  protected void setClosureInf(List<ClosureInf> plist) {
    this.plist = plist.toArray(new ClosureInf[0]);
  }


  protected Object tableGet(Object table, Object key) {
    return t.tableGet(table, key);
  }


  protected void tableSet(Object o, Object k, Object v) {
    t.tableSet(o, k, v);
  }


  protected Object getRegisterOrConstant(LuaCallFrame c, int i, Prototype p) {
    return t.getRegisterOrConstant(c, i, p);
  }


  protected void fail(String msg) {
    throw new LuaFail(msg);
  }


  //TODO: optimization
  protected Double rawTonumber(Object o) {
    return KahluaUtil.rawTonumber(o);
  }


  protected Object getBinMetaOp(Object a, Object b, String meta_op) {
    return t.getBinMetaOp(a, b, meta_op);
  }


  protected Object getMetaOp(Object a, String meta) {
    return t.getMetaOp(a, meta);
  }


  protected Object getCompMetaOp(Object a, Object b, String meta_op) {
    return t.getCompMetaOp(a, b, meta_op);
  }


  protected Object metaOp(Object a, Object b, String op) {
    Object metafun = getBinMetaOp(a, b, op);
    if (metafun == null) {
      throw new LuaFail("["+ op +"] not defined for operands");
    }
    return call(metafun, a, b, null);
  }


  protected Object call(Object func, Object a1, Object a2, Object a3) {
    return t.call(func, a1, a2, a3);
  }


  protected void auto_op_concat(int a, int b, int c, LuaCallFrame callFrame) {
    int first = b;
    int last = c;
    //Tool.pl("concat", a, b, c, callFrame.localBase);

    Object res = callFrame.get(last);
    last--;
    while (first <= last) {
      // Optimize for multi string concats
      {
        String resStr = KahluaUtil.rawTostring(res);

        if (resStr != null) {
          int nStrings = 0;
          int pos = last;
          while (first <= pos) {
            Object o = callFrame.get(pos);
            pos--;
            if (KahluaUtil.rawTostring(o) == null) {
              break;
            }
            nStrings++;
          }

          if (nStrings > 0) {
            StringBuffer concatBuffer = new StringBuffer();

            int firstString = last - nStrings + 1;
            while (firstString <= last) {
              Object o = callFrame.get(firstString);
              concatBuffer.append(KahluaUtil.rawTostring(o));
              firstString++;
            }

            concatBuffer.append(resStr);
            res = concatBuffer.toString();
            last = last - nStrings;
          }
        }
      }

      if (first <= last) {
        Object leftConcat = callFrame.get(last);

        Object metafun = getBinMetaOp(leftConcat, res, "__concat");
        if (metafun == null) {
          KahluaUtil.fail(("__concat not defined for operands: "
            + leftConcat + " and " + res));
        }
        res = call(metafun, leftConcat, res, null);
        last--;
      }
    }
    callFrame.set(a, res);
  }


  protected boolean try_comp_le(Object bo, Object co) {
    Object metafun = getCompMetaOp(bo, co, "__le");
    boolean invert = false;
    boolean resBool;

    /*
     * Special case: OP_LE uses OP_LT if __le is not
     * defined. a <= b is then translated to not (b < a)
     */
    if (metafun == null) {
      metafun = getCompMetaOp(bo, co, "__lt");

      // Swap the objects
      Object tmp = bo;
      bo = co;
      co = tmp;

      // Invert a (i.e. add the "not"
      invert = true;
    }

    if (metafun == null) {
      KahluaUtil.fail("__le not defined for operand");
    }

    Object res = call(metafun, bo, co, null);
    resBool = KahluaUtil.boolEval(res);

    if (invert) {
      resBool = !resBool;
    }
    return resBool;
  }


  protected boolean try_comp_lt(Object bo, Object co) {
    Object metafun = getCompMetaOp(bo, co, "__lt");

    if (metafun == null) {
      KahluaUtil.fail("__le not defined for operand");
    }

    Object res = call(metafun, bo, co, null);
    return KahluaUtil.boolEval(res);
  }


  protected boolean try_comp_eq(Object bo, Object co) {
    Object metafun = getCompMetaOp(bo, co, "__eq");
    boolean resBool;

    if (metafun == null) {
      return BaseLib.luaEquals(bo, co);
    }

    Object res = call(metafun, bo, co, null);
    return KahluaUtil.boolEval(res);
  }


  public void call(ClosureInf ci, LuaCallFrame fr, Object orgFunction,
                   int nArguments2, int argBase) {
    if (orgFunction == null)
      throw new LuaFail("Tried to call nil");

    ComputStack cs = new ComputStack(fr, argBase, nArguments2);
    Object function = orgFunction;


    for (;;) {
      if (function instanceof JavaFunction) {
        call((JavaFunction) function, cs);
        break;
      }
      else if (function instanceof ClosureInf) {
        ClosureInf newCI = (ClosureInf) function;
        newCI.frameParams(cs);
        newCI.call();
        break;
      }
      else if (function instanceof LuaClosure) {
        call((LuaClosure) function, cs);
        break;
      }

      Object funcMeta = this.getMetaOp(function, "__call");
      if (funcMeta == null) {
        String errMsg = "Object " + orgFunction + " did not have __call metatable set";
        throw new LuaFail(errMsg);
      }

      cs = new ComputStack(
        cs.top,
        nArguments2 + 1, // nArguments2 += 1;
        cs.returnBase, // localBase2 = returnBase2;
        cs.returnBase
      );
      function = funcMeta;
    }
  }


  private void call(JavaFunction javaf, ComputStack cs) {
    LuaCallFrame oframe = cs.pushFrame(coroutine, javaf);
    int nReturnValues = javaf.call(oframe, cs.nArguments);

    int top = oframe.getTop();
    int actualReturnBase = top - nReturnValues;

    int diff = oframe.returnBase - oframe.localBase;
    oframe.stackCopy(actualReturnBase, diff, nReturnValues);
    oframe.setTop(nReturnValues + diff);

    this.coroutine.popCallFrame();
  }


  /**
   * LuaClosure may be the result of running the old vm.
   * Their data structures are compatible, and a new version of the
   * virtual machine can be created to compile and run it.
   */
  private void call(LuaClosure c, ComputStack cs) {
    KahluaThread2 t = new KahluaThread2(platform, coroutine.environment);
    t.call(c, coroutine, cs.nArguments);
  }


  protected void pushVarargs(Prototype prototype, LuaCallFrame fr, int index, int n) {
    int nParams = prototype.numParams;
    int nVarargs = fr.nArguments - nParams;
    if (nVarargs < 0) nVarargs = 0;
    if (n == -1) {
      n = nVarargs;
      fr.setTop(index + n);
    }
    if (nVarargs > n) nVarargs = n;

    fr.stackCopy(-fr.nArguments + nParams, index, nVarargs);

    int numNils = n - nVarargs;
    if (numNils > 0) {
      fr.stackClear(index + nVarargs, index + n - 1);
    }
  }
}