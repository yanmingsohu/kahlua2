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

import org.objectweb.asm.signature.SignatureVisitor;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;


public class Tool {

  public static final int STACK_DEPTH = 2;
  public static final char sp = ' ';
  public static final char ent = '\n';
  public static final char tab = '\t';
  public static final String nil = "nil";
  public static final String point = " -- ";
  public static final String spoint = " +> ";

  private static int line = 0;


  public static void pl(Object o) {
    StringBuilder buf = new StringBuilder();
    buf.append(line++).append("^ ");
    getCurrentStack(buf, STACK_DEPTH);
    buf.append(" - ");
    buf.append(o);
    System.out.println(buf);
  }


  public static void pl(Object ...o) {
    plx(STACK_DEPTH +1, o);
  }


  public static void plx(int stackDepth, Object ...o) {
    StringBuilder buf = new StringBuilder();
    buf.append(line++).append("^ ");
    getCurrentStack(buf, stackDepth);
    buf.append(" - ");
    for (int i=0; i<o.length; ++i) {
      buf.append(sp);
      buf.append(o[i]);
    }
    System.out.println(buf.toString());
  }


  public static StringBuilder getCurrentStack(StringBuilder b, int stackDepth) {
    Exception e = new Exception();
    StackTraceElement[] ss = e.getStackTrace();

    b.append(ss[stackDepth].getMethodName());
    b.append('(');
    b.append(ss[stackDepth].getFileName());
    b.append(':');
    int ln = ss[stackDepth].getLineNumber();
    if (ln < 999) b.append(sp);
    if (ln < 99) b.append(sp);
    if (ln < 9) b.append(sp);
    b.append(ln);
    b.append(')');

    //StackWalker.getInstance().walk(Tool::getFrame);
    return b;
  }


//  public static List<StackWalker.StackFrame>
//  getFrame(Stream<StackWalker.StackFrame> sfs) {
//    Tool.pl(sfs);
//    return null;
//  }


  public static void typeName(SignatureVisitor w, Type t) {
    if (t instanceof Class) {
      Class c = (Class) t;
      if (c.isPrimitive()) {
        // BUG, when call visitBaseType() has '>'
        w.visitBaseType(Character.toUpperCase(t.getTypeName().charAt(0)));
        return;
      } else if (c.isArray()) {
        w.visitArrayType().visitClassType(toClassPath(c));
        return;
      }
    }
    w.visitClassType(toClassPath(t.getTypeName()));
  }


  public static void typeName(StringBuilder b, Type t) {
    if (t instanceof Class) {
      final Class c = (Class) t;
      if (c.isPrimitive()) {
        b.append(primitiveName(c));
        return;
      } else if (c.isArray()) {
        b.append(toClassPath(c));
        return;
      }
    }
    b.append('L');
    b.append(toClassPath(t.getTypeName()));
    b.append(';');
  }


  public static char primitiveName(Class c) {
    if (c == void.class)    return ('V');
    if (c == boolean.class) return ('Z');
    if (c == int.class)     return ('I');
    if (c == double.class)  return ('D');
    if (c == byte.class)    return ('B');
    if (c == char.class)    return ('C');
    if (c == short.class)   return ('S');
    if (c == long.class)    return ('J');
    if (c == float.class)   return ('F');
    //b.append(Character.toUpperCase(t.getTypeName().charAt(0)));
    throw new RuntimeException("Not primitive "+ c);
  }


  public static String getMethodSi(Method m) {
    StringBuilder buf = new StringBuilder(30);
    buf.append('(');
    for (Type pt : m.getParameterTypes()) {
      typeName(buf, pt);
    }
    buf.append(')');
    typeName(buf, m.getReturnType());
    return buf.toString();
  }


  public static String toClassPath(Class c) {
    return toClassPath(c.getName());
  }


  public static String toClassPath(String className) {
    return swap(className, '.', '/');
  }


  public static String toClassName(String classPath) {
    return swap(classPath, '/', '.');
  }


  public static String toLocalVarName(Class c) {
    String name = c.getSimpleName();
    return "_"+ Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }


  public static String formatClassName(String path) {
    StringBuilder r = new StringBuilder();
    int state = 0;
    int li = -1;
    int ni = 0;

    for (int i=0; i<path.length(); ++i) {
      char c = path.charAt(i);
      switch (state) {
        case 0:
          if (c != '.' && c != '/' && c != '\\') {
            state = 1;
            r.append(c);
          }
          break;

        case 1:
          if (c == '/' || c == '\\') {
            r.append('.');
            ni = r.length();
          } else {
            if (c == '.') li = r.length();
            r.append(c);
          }
          break;
      }
    }
    r.setCharAt(ni, Character.toUpperCase(r.charAt(ni)));
    if (li >= 0) {
      return r.substring(0, li);
    }
    return r.toString();
  }


  public static String swap(String from, char a, char to) {
    StringBuilder r = new StringBuilder();
    for (int i=0; i<from.length(); ++i) {
      final char c = from.charAt(i);
      if (c == a) {
        r.append(to);
      } else {
        r.append(c);
      }
    }
    return r.toString();
  }


  public static String flatPath(String f) {
    StringBuilder out = new StringBuilder();
    for (int i=0; i<f.length(); ++i) {
      char c = f.charAt(i);
      switch (c) {
        default: out.append(c); break;
        case '_': out.append("__"); break;
        case '\\':
        case '/': out.append('_'); break;
      }
    }
    return out.toString();
  }


  public static String num8len(String x) {
    switch (x.length()) {
      default: return x;
      case 0: return "00000000";
      case 1: return "0000000" +x;
      case 2: return "000000" +x;
      case 3: return "00000" +x;
      case 4: return "0000" +x;
      case 5: return "000" +x;
      case 6: return "00" +x;
      case 7: return "0" +x;
    }
  }


  public static String str8len(String x) {
    switch (x.length()) {
      default: return x;
      case 0: return "        ";
      case 1: return "       " +x;
      case 2: return "      " +x;
      case 3: return "     " +x;
      case 4: return "    " +x;
      case 5: return "   " +x;
      case 6: return "  " +x;
      case 7: return " " +x;
    }
  }


  public static String str8len(int x) {
    return str8len(x +"");
  }


  public static String num8len(int i) {
    return num8len(Integer.toHexString(i));
  }


  public static void objectArray2String(StringBuilder out, Object[] s) {
    new Array2String(out, s).render();
  }


  public static void objectArray2String(StringBuilder out, Object[] s, int base, ISelect st) {
    new Array2String(out, s, base, st).render();
  }


  public static String hash(Object o) {
    return o == null ? "NULL" : Integer.toHexString(System.identityHashCode(o));
  }


  public static void printTable(KahluaTable t) {
    StringBuilder out = new StringBuilder();
    KahluaTableIterator it = t.iterator();
    while (it.advance()) {
      out.append(ent).append(tab);
      out.append(it.getKey());
      out.append("\t:\t");
      out.append(it.getValue());
    }
    plx(STACK_DEPTH +1, "Table", t, out);
  }


  public static PrintStream makePrintStream() {
    return new PrintStream(System.out) {
      public void println(String x) {
        plx(STACK_DEPTH +5, x);
      }
    };
  }
}
