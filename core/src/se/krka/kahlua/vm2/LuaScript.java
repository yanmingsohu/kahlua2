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


  public void reinit(KahluaThread2 kt2, Coroutine c) {
    this.t = kt2;
    this.coroutine = c;
    this.platform = kt2.platform;
    this.plist[IConst.rootClosure].frameParams(0, 0, 0, true);
  }


  protected void setClosureInf(List<ClosureInf> plist) {
    this.plist = plist.toArray(new ClosureInf[0]);
  }


  protected void closureReturn(LuaCallFrame cf) {
    coroutine.popCallFrame();
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


  protected void auto_op_concat(int a, int b, int c, LuaCallFrame callFrame) {
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
          printLuaStack();
          KahluaUtil.fail(("__concat not defined for operands: "
            + leftConcat + " and " + res));
        }
        res = call(metafun, leftConcat, res, null);
        last--;
      }
    }
    callFrame.set(a, res);
  }


  private void printLuaStack() {
    Tool.pl("Lua stack:");
    Object[] s = coroutine.objectStack;
    for (int i=0; i<s.length; ++i) {
      if (s[i] != null) {
        Tool.pl(i, s[i].getClass(), s[i]);
      } else {
        Tool.pl(i, "NULL");
      }
    }
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

}