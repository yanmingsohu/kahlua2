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


import se.krka.kahlua.stdlib.BaseLib;
import se.krka.kahlua.vm.*;

import java.util.List;

import static se.krka.kahlua.vm.KahluaThread.*;


public abstract class LuaScript implements Runnable {


  protected Platform platform;
  protected KahluaThread2 t;
  protected Coroutine coroutine;
  protected ClosureInf[] plist;


  public LuaScript() {
  }


  public void reinit(KahluaThread2 kt2, Coroutine c) {
    this.t = kt2;
    this.coroutine = c;
    this.platform = kt2.platform;
  }


  protected void setClosureInf(List<ClosureInf> plist) {
    this.plist = plist.toArray(new ClosureInf[0]);
  }


  protected LuaCallFrame newFrame(LuaClosure cl) {
    LuaCallFrame cf = coroutine.pushNewCallFrame(cl, null, 0, 0, 0, false, false);
    cf.init();
    return cf;
  }


  protected LuaClosure newClosure(int index) {
    return new LuaClosure(plist[index].prototype, coroutine.environment);
  }


  protected Prototype findPrototype(int index) {
    return plist[index].prototype;
  }


  Object tableGet(Object table, Object key) {
    return t.tableGet(table, key);
  }


  void tableSet(Object o, Object k, Object v) {
    t.tableSet(o, k, v);
  }


  Object getRegisterOrConstant(LuaCallFrame c, int i, Prototype p) {
    return t.getRegisterOrConstant(c, i, p);
  }


  void fail(String msg) {
    throw new RuntimeException(msg);
  }

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
      fail("["+ op +"] not defined for operands");
    }
    return call(metafun, a, b, null);
  }

  protected Object call(Object func, Object a1, Object a2, Object a3) {
    return t.call(func, a1, a2, a3);
  }

  void auto_op_concat(int a, int b, int c, LuaCallFrame callFrame) {
    int first = b;
    int last = c;

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
              concatBuffer.append(KahluaUtil
                .rawTostring(callFrame
                  .get(firstString)));
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

        Object metafun = getBinMetaOp(leftConcat, res,
          "__concat");
        if (metafun == null) {
          KahluaUtil.fail(("__concat not defined for operands: " + leftConcat + " and " + res));
        }
        res = call(metafun, leftConcat, res, null);
        last--;
      }
    }
    callFrame.set(a, res);
  }

  protected void auto_op_eq(int a, int b, int c, int opcode,
                  LuaCallFrame callFrame,
                  Prototype prototype) {
    Object bo = getRegisterOrConstant(callFrame, b, prototype);
    Object co = getRegisterOrConstant(callFrame, c, prototype);

    if (bo instanceof Double && co instanceof Double) {
      double bd_primitive = KahluaUtil.fromDouble(bo);
      double cd_primitive = KahluaUtil.fromDouble(co);

      if (opcode == OP_EQ) {
        if ((bd_primitive == cd_primitive) == (a == 0)) {
          callFrame.pc++;
        }
      } else {
        if (opcode == OP_LT) {
          if ((bd_primitive < cd_primitive) == (a == 0)) {
            callFrame.pc++;
          }
        } else { // opcode must be OP_LE
          if ((bd_primitive <= cd_primitive) == (a == 0)) {
            callFrame.pc++;
          }
        }
      }
    } else if (bo instanceof String && co instanceof String) {
      if (opcode == OP_EQ) {
        if ((bo.equals(co)) == (a == 0)) {
          callFrame.pc++;
        }
      } else {
        String bs = (String) bo;
        String cs = (String) co;
        int cmp = bs.compareTo(cs);

        if (opcode == OP_LT) {
          if ((cmp < 0) == (a == 0)) {
            callFrame.pc++;
          }
        } else { // opcode must be OP_LE
          if ((cmp <= 0) == (a == 0)) {
            callFrame.pc++;
          }
        }
      }
    } else {
      boolean resBool;
      if (bo == co && opcode == OP_EQ) {
        resBool = true;
      } else {
        boolean invert = false;

        String meta_op = t.metaOpName(opcode);

        Object metafun = getCompMetaOp(bo, co, meta_op);

        /*
         * Special case: OP_LE uses OP_LT if __le is not
         * defined. a <= b is then translated to not (b < a)
         */
        if (metafun == null && opcode == OP_LE) {
          metafun = getCompMetaOp(bo, co, "__lt");

          // Swap the objects
          Object tmp = bo;
          bo = co;
          co = tmp;

          // Invert a (i.e. add the "not"
          invert = true;
        }

        if (metafun == null && opcode == OP_EQ) {
          resBool = BaseLib.luaEquals(bo, co);
        } else {
          if (metafun == null) {
            KahluaUtil.fail((meta_op + " not defined for operand"));
          }
          Object res = call(metafun, bo, co, null);
          resBool = KahluaUtil.boolEval(res);
        }

        if (invert) {
          resBool = !resBool;
        }
      }
      if (resBool == (a == 0)) {
        callFrame.pc++;
      }
    }
  }

  protected void auto_op_call(int a, int b, int c, LuaCallFrame callFrame) {
    int nArguments2 = b - 1;
    if (nArguments2 != -1) {
      callFrame.setTop(a + nArguments2 + 1);
    } else {
      nArguments2 = callFrame.getTop() - a - 1;
    }
  }

  protected void auto_op_tailcall(int a, int b) {
  }

  protected void auto_op_return(int a, int b) {
  }

  protected void auto_op_forprep(int a, int b, LuaCallFrame callFrame) {
    double iter = KahluaUtil.fromDouble(callFrame.get(a));
    double step = KahluaUtil.fromDouble(callFrame.get(a + 2));
    callFrame.set(a, KahluaUtil.toDouble(iter - step));
    callFrame.pc += b;
  }

  protected void auto_op_forloop(int a, int b, LuaCallFrame callFrame) {
    double iter = KahluaUtil.fromDouble(callFrame.get(a));
    double end = KahluaUtil.fromDouble(callFrame.get(a + 1));
    double step = KahluaUtil.fromDouble(callFrame.get(a + 2));
    iter += step;
    Double iterDouble = KahluaUtil.toDouble(iter);
    callFrame.set(a, iterDouble);

    if ((step > 0) ? iter <= end : iter >= end) {
      callFrame.pc += b;
      callFrame.set(a + 3, iterDouble);
    } else {
      callFrame.clearFromIndex(a);
    }
  }

  protected void auto_op_tforloop(int a, int c) {
//    callFrame.setTop(a + 6);
//    callFrame.stackCopy(a, a + 3, 3);
//    callFrame.clearFromIndex(a + 3 + c);
//    callFrame.setPrototypeStacksize();
//
//    Object aObj3 = callFrame.get(a + 3);
//    if (aObj3 != null) {
//      callFrame.set(a + 2, aObj3);
//    } else {
//      callFrame.pc++;
//    }
  }

  protected void auto_op_setlist(int a, int b, int c) {
  }

  protected void auto_op_close(int a, LuaCallFrame callFrame) {
    callFrame.closeUpvalues(a);
  }

  protected void auto_op_closure(int a, int b, int pindex,
                       LuaCallFrame callFrame,
                       Prototype prototype) {
  }

  protected void auto_op_vararg(int a, int b, LuaCallFrame callFrame) {
    callFrame.pushVarargs(a, b);
  }
}