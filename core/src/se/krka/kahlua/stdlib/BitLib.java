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

package se.krka.kahlua.stdlib;

import se.krka.kahlua.vm.*;

public class BitLib {

  public static void register(Platform platform, KahluaTable env) {
    KahluaTable bit = platform.newTable();
    env.rawset("bit", bit);

    reg(bit, "tobit", new tobit());
    reg(bit, "tohex", new tohex());
    reg(bit, "bnot", new bnot());
    reg(bit, "bor", new bor());
    reg(bit, "band", new band());
    reg(bit, "bxor", new bxor());
    reg(bit, "lshift", new lshift());
    reg(bit, "rshift", new rshift());
    reg(bit, "arshift", new arshift());
    reg(bit, "rol", new rol());
    reg(bit, "ror", new ror());
    reg(bit, "bswap", new bswap());
    bit.rawset("isJavabit", true);
  }


  private static void reg(KahluaTable self, String name, FuncBase f) {
    f.setName(name);
    f.reg(self);
  }


  private static abstract class FuncBase implements JavaFunction {
    protected String name;

    void setName(String n) {
      this.name = n;
    }

    void reg(KahluaTable self) {
      self.rawset(name, this);
    }

    int gi(LuaCallFrame cf, int index) {
      return (int) gl(cf, index);
    }

    long gl(LuaCallFrame cf, int index) {
      Object o = KahluaUtil.getArg(cf, index, name);
      if (o instanceof Double) {
        return (long)(double) o;
      }
      if (o instanceof String) {
        return Long.valueOf((String) o);
      }
      return 0;
    }
  }



  private static class tobit extends FuncBase {
    public int call(LuaCallFrame cf, int nArguments) {
      int a = gi(cf, 1);
      cf.push(KahluaUtil.toDouble(a));
      return 1;
    }
  }


  private static class tohex extends FuncBase {
    final static String[] n0 = new String[]{
      "00000000",
      "0000000",
      "000000",
      "00000",
      "0000",
      "000",
      "00",
      "0",
      "",
    };


    public int call(LuaCallFrame cf, int nArguments) {
      int a = gi(cf, 1);
      int n = nArguments >= 2 ? gi(cf, 2) : 8;
      int l = Math.abs(n);

      String s = Integer.toHexString(a);
      String r = n0[s.length()] + s;
      if (n < 0) {
        r = r.toUpperCase();
      }
      cf.push(r.substring(8-l, 8));
      return 1;
    }
  }


  private static class bnot extends FuncBase {
    public int call(LuaCallFrame cf, int nArguments) {
      int a = gi(cf, 1);
      cf.push(KahluaUtil.toDouble(~a));
      return 1;
    }
  }


  private static class bor extends FuncBase {
    public int call(LuaCallFrame cf, int nArguments) {
      int r = gi(cf, 1);
      for (int i=2; i<=nArguments; ++i) {
        r = r | gi(cf, i);
      }
      cf.push(KahluaUtil.toDouble(r));
      return 1;
    }
  }


  private static class band extends FuncBase {
    public int call(LuaCallFrame cf, int nArguments) {
      int r = gi(cf, 1);
      for (int i=2; i<=nArguments; ++i) {
        r = r & gi(cf, i);
      }
      cf.push(KahluaUtil.toDouble(r));
      return 1;
    }
  }


  private static class bxor extends FuncBase {
    public int call(LuaCallFrame cf, int nArguments) {
      int r = gi(cf, 1);
      for (int i=2; i<=nArguments; ++i) {
        r = r ^ gi(cf, i);
      }
      cf.push(KahluaUtil.toDouble(r));
      return 1;
    }
  }


  private static class lshift extends FuncBase {
    public int call(LuaCallFrame cf, int nArguments) {
      int a = gi(cf, 1);
      int n = gi(cf, 2);
      cf.push(KahluaUtil.toDouble(a << n));
      return 1;
    }
  }


  private static class rshift extends FuncBase {
    public int call(LuaCallFrame cf, int nArguments) {
      int a = gi(cf, 1);
      int n = gi(cf, 2);
      cf.push(KahluaUtil.toDouble(a >>> n));
      return 1;
    }
  }


  private static class arshift extends FuncBase {
    public int call(LuaCallFrame cf, int nArguments) {
      int a = gi(cf, 1);
      int n = gi(cf, 2);
      cf.push(KahluaUtil.toDouble(a >> n));
      return 1;
    }
  }


  private static class rol extends FuncBase {
    final static int H = 1 << 31;
    final static int L = 1;

    public int call(LuaCallFrame cf, int nArguments) {
      int a = gi(cf, 1);
      int n = gi(cf, 2);
      for (int i=0; i<n; ++i) {
        int h = H & a;
        a = a << 1;
        if (h != 0) {
          a |= L;
        }
      }
      cf.push(KahluaUtil.toDouble(a));
      return 1;
    }
  }


  private static class ror extends FuncBase {
    final static int H = 1 << 31;
    final static int L = 1;

    public int call(LuaCallFrame cf, int nArguments) {
      int a = gi(cf, 1);
      int n = gi(cf, 2);
      for (int i=0; i<n; ++i) {
        int l = L & a;
        a = a >>> 1;
        if (l != 0) {
          a |= H;
        }
      }
      cf.push(KahluaUtil.toDouble(a));
      return 1;
    }
  }


  private static class bswap extends FuncBase {
    public int call(LuaCallFrame cf, int nArguments) {
      int a = gi(cf, 1);
      int x1 = (a & 0xFF00_0000) >>> 24;
      int x2 = (a & 0x00FF_0000) >>> 8;
      int x3 = (a & 0x0000_FF00) << 8;
      int x4 = (a & 0x0000_00FF) << 24;
      cf.push(KahluaUtil.toDouble(x1 | x2 | x3 | x4));
      return 1;
    }
  }


  public static void pl(Object ...o) {
    StringBuilder buf = new StringBuilder();
    for (int i=0; i<o.length; ++i) {
      buf.append(o[i]);
      buf.append(" ");
    }
    System.out.println(buf.toString());
  }
}
