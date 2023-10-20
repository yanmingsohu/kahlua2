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

public class Array2String {

  public static final String nil = "nil";
  public static final String point = " -- ";
  public static final String spoint = " +> ";
  public static final char ent = Tool.ent;
  public static final char sp = Tool.sp;

  final StringBuilder out;
  final int len;
  final int base;

  int i;
  int ns = 0;
  int ne = 0;
  int nstate = 0;
  Object obj;
  String pt;
  boolean isCh;


  public Array2String(StringBuilder out, Object[] oa) {
    this(out, oa, 0, i-> false);
  }


  public Array2String(StringBuilder out, Object[] oa, int base, ISelect st) {
    this.base = base;
    this.out = out;
    len = oa.length;

    for (i=0; i< len; ++i) {
      isCh = st.isChoise(i - base);
      pt = isCh ? spoint : point;
      obj = oa[i];

      switch(nstate) {
        case 0:
          if (obj != null) {
            showObj();
          } else {
            nstate = 1;
            ns = i;
            ne = i;
          }
          break;

        case 1:
          if (obj != null) {
            showNil();
            showObj();
            nstate = 0;
          } else {
            if (isCh) {
              showCh();
            }
          }
          ne = i;
          break;
      }
    }

    ne = i-1;
    isCh = st.isChoise(ne);
    if (oa[ne] == null) {
      showNil();
    }
  }


  void showCh() {
    if (ns != i) {
      out.append(ent).append(__S8(ns)).append("~");
    }
    out.append(ent).append(__S8(i)).append(spoint).append(nil);
    ns = i+1;
    ne = i+1;
  }


  void showNil() {
    if (ns == ne) {
      out.append(ent).append(__S8(ns)).append(pt).append(nil);
    } else {
      out.append(ent).append(__S8(ns)).append("~\n")
        .append(__S8(ne)).append(pt).append(nil);
    }
  }


  void showObj() {
    String addr = Integer.toHexString( System.identityHashCode(obj) );
    String desc = obj.getClass().getName() +"@"+ addr;
    String str  = obj.toString();

    out.append(ent).append(__S8(i)).append(pt).append(desc);
    if (! desc.equals(str)) {
      out.append(sp).append('"').append(str).append('"');
    }
  }


  String __S8(int x) {
    if ((base > 0) && (x-base == 0)) return "------ 0";
    return Tool.str8len(x - base);
  }


  public String toString() {
    return out.toString();
  }
}
