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

import static se.krka.kahlua.vm2.KahluaThread2.*;


/**
 * Compile debugging information into class files
 */
public class DebugInf implements IConst {

  private final static int NotReg = Integer.MIN_VALUE;
  private final static int NotConst = Integer.MIN_VALUE;
  private final static int BUF_SIZE = 100;
  private final static char SP = Tool.sp;

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


  private ClassMaker cm;
  private String classPath;
  private int line;
  private int opcode;
  private int op;
  private int pc;


  public DebugInf() {
  }


  public void update(ClassMaker cm, String classPath) {
    this.cm = cm;
    this.classPath = classPath;
  }


  public void update(int line, int opcode, int op, int pc) {
    this.line = line;
    this.opcode = opcode;
    this.op = op;
    this.pc = pc;
  }


  public void fullMsg() {
    cm.vPrint("[ "+ opDesc() +" ]");
  }


  public void shortMsg() {
    String msg = classPath +":"+ line +" "+ KahluaThread2.opName(opcode);
    switch (opcode) {
      default:
        msg += "{ A:"+ getA8(op) +" B:"+ getB9(op) +" C:"+ getC9(op) + " }";
        break;

      case OP_MOVE:
      case OP_LOADNIL:
      case OP_GETUPVAL:
      case OP_SETUPVAL:
      case OP_UNM:
      case OP_NOT:
      case OP_LEN:
      case OP_TAILCALL:
      case OP_RETURN:
      case OP_CLOSURE:
      case OP_VARARG:
        msg += "{ A:"+ getA8(op) +" B:"+ getB9(op) +" }";
        break;

      case OP_NEWTABLE:
      case OP_CLOSE:
        msg += "{ A:"+ getA8(op) +" }";

      case OP_LOADK:
      case OP_GETGLOBAL:
      case OP_SETGLOBAL:
        msg += "{ A:"+ getA8(op) +" Bx:" + getBx(op) +" }";
        break;

      case OP_FORPREP:
      case OP_FORLOOP:
        msg += "{ A:"+ getA8(op) +" SBx:" + getSBx(op) +" }";
        break;

      case OP_JMP:
        msg += "{ SBx:" + getSBx(op) +" }";
        break;

      case OP_TFORLOOP:
        msg += "{ A:"+ getA8(op) +" C:"+ getC9(op) + " }";
        break;
    }

    cm.vPrint(msg);
  }


  public void stackAll() {
    cm.vPrintStack();
    cm.vPrintConsts();
  }


  /**
   * Stack offset not calculated
   */
  public void stack() {
    final int a = getA8(op);
    final int b = getB9(op);
    final int c = getC9(op);
    final int bx = getBx(op);

    switch (opcode) {
      case OP_MOVE:
      case OP_UNM:
      case OP_NOT:
      case OP_LEN:
      case OP_TESTSET:
        cm.vPrintStack(a, b);
        break;

      case OP_SETUPVAL:
      case OP_GETUPVAL:
        cm.vPrintOldClosureUpvaleu(b);
        cm.vPrintStack(a);
        break;

      case OP_LOADK:
        cm.vPrintConsts(bx);
        //nobreak;

      case OP_LOADBOOL:
      case OP_LOADNIL:
      case OP_NEWTABLE:
      case OP_TEST:
      case OP_CALL:
      case OP_TAILCALL:
      case OP_RETURN:
      case OP_FORLOOP:
      case OP_FORPREP:
      case OP_TFORLOOP:
      case OP_SETLIST:
      case OP_VARARG:
      case OP_CLOSE:
      case OP_CLOSURE: // show Up values after run
        cm.vPrintStack(a);
        break;

      case OP_GETTABLE:
      case OP_SELF:
        cm.vPrintConsts(RKK(c));
        cm.vPrintStack(a, b, RKR(c));
        break;

      case OP_SETTABLE:
      case OP_ADD:
      case OP_SUB:
      case OP_MUL:
      case OP_DIV:
      case OP_MOD:
      case OP_POW:
        cm.vPrintConsts(RKK(b), RKK(c));
        cm.vPrintStack(a, RKR(b), RKR(c));
        break;

      case OP_CONCAT:
        cm.vPrintStack(a, b, c);
        break;

      case OP_JMP:
        break;

      case OP_EQ:
      case OP_LT:
      case OP_LE:
        cm.vPrintConsts(RKK(b), RKK(c));
        cm.vPrintStack(RKR(b), RKR(c));
        break;

      case OP_GETGLOBAL:
      case OP_SETGLOBAL:
        cm.vPrintConsts(bx);
        cm.vPrintStack(a);
        break;
    }
  }


  private int RKR(int i) {
    return i<LuaConstVarBegin ? i : NotReg;
  }


  private int RKK(int i) {
    return i<LuaConstVarBegin ? NotConst : i-LuaConstVarBegin;
  }


  public void build() {
    Tool.pl("LL ",pc, Tool.num8len(op), opNames[opcode], line);
  }


  public String opDesc() {
    final int a = getA8(op);
    final int b = getB9(op);
    final int c = getC9(op);
    final int bx = getBx(op);
    final int sbx = getSBx(op);

    switch (opcode) {
      case OP_MOVE:
        return String.format("MOVE  A B     R(A:%d) := R(B:%d)", a, b);
      case OP_LOADK:
        return String.format("LOADK A Bx    R(A:%d) := Kst(Bx:%d)", a, b);
      case OP_LOADBOOL:
        return String.format("LDBOL A B C   R(A:%d) := (Bool)B:%d; if (C:%d) pc++", a, b, c);
      case OP_LOADNIL:
        return String.format("LDNIL A B     R(A:%d), R(A:%d+1), ..., R(A:%d+B:%d) := nil", a, a, a, b);
      case OP_GETUPVAL:
        return String.format("GTUVL A B     R(A:%d) := UpValue[B:%d]", a, b);
      case OP_GETTABLE:
        return String.format("GTTBE A B C   R(A:%d) := R(B:%d)[RK(C:%d)]", a, b, c);
      case OP_SETUPVAL:
        return String.format("STUVL A B     UpValue[B:%d] := R(A:%d)", b, a);
      case OP_SETTABLE:
        return String.format("STTBL A B C   R(A:%d)[RK(B:%d)] := RK(C:%d)", a, b, c);
      case OP_NEWTABLE:
        return String.format("NETBL A B C   R(A:%d) := {} (size = B:%d,C:%d)", a, b, c);
      case OP_SELF:
        return String.format("SELF  A B C   R(A:%d+1) := R(B:%d); R(A:%d) := R(B:%d)[RK(C:%d)]", a, b, a, b, c);
      case OP_ADD:
        return String.format("ADD   A B C   R(A:%d) := RK(B:%d) + RK(C:%d)", a, b, c);
      case OP_SUB:
        return String.format("SUB   A B C   R(A:%d) := RK(B:%d) - RK(C:%d)", a, b, c);
      case OP_MUL:
        return String.format("MUL   A B C   R(A:%d) := RK(B:%d) * RK(C:%d)", a, b, c);
      case OP_DIV:
        return String.format("DIV   A B C   R(A:%d) := RK(B:%d) / RK(C:%d)", a, b, c);
      case OP_MOD:
        return String.format("MOD   A B C   R(A:%d) := RK(B:%d) % RK(C:%d)", a, b, c);
      case OP_POW:
        return String.format("POW   A B C   R(A:%d) := RK(B:%d) ^ RK(C:%d)", a,b,c);
      case OP_UNM:
        return String.format("UNM   A B     R(A:%d) := -R(B:%d)", a,b);
      case OP_NOT:
        return String.format("NOT   A B     R(A:%d) := not R(B:%d)", a,b);
      case OP_LEN:
        return String.format("LEN   A B     R(A:%d) := length of R(B:%d)", a, b);
      case OP_CONCAT:
        return String.format("CONCT A B C   R(A:%d) := R(B:%d).. ... ..R(C:%d)", a, b, c);
      case OP_JMP:
        return String.format("JMP   A sBx   pc+=sBx:%d; if (A:%d) close all upvalues >= R(A:%d - 1)", sbx, a, a);
      case OP_EQ:
        return String.format("EQ    A B C   if ((RK(B:%d) == RK(C:%d)) ~= A:%d) then PC++", b, c, a);
      case OP_LT:
        return String.format("LT    A B C   if ((RK(B:%d) <  RK(C:%d)) ~= A:%d) then PC++", b, c, a);
      case OP_LE:
        return String.format("LE    A B C   if ((RK(B:%d) <= RK(C:%d)) ~= A:%d) then PC++", b, c, a);
      case OP_TEST:
        return String.format("TEST  A C     if (boolean(R(A:%d)) != C:%d) then PC++", a, c);
      case OP_TESTSET:
        return String.format("TESSE A B C   if (boolean(R(B:%d)) != C:%d) then PC++ else R(A:%d) := R(B:%d)", b, c, a, b);
      case OP_CALL:
        return String.format("CALL  A B C   R(A:%d), ... ,R(A:%d+C:%d-2) := R(A:%d)(R(A:%d+1), ... ,R(A:%d+B:%d-1))", a, a, c, a, a, a, b);
      case OP_TAILCALL:
        return String.format("TCALL A B C   return R(A:%d)(R(A:%d+1), ... ,R(A:%d+B:%d-1))", a, a, a, b);
      case OP_RETURN:
        return String.format("RETUN A B     return R(A:%d), ... ,R(A:%d+B:%d-2)", a,a,b);
      case OP_FORLOOP:
        return String.format("FOLOP A sBx   R(A:%d)+=R(A:%d+2); If R(A:%d) <?= R(A:%d+1) then { pc+=sBx:%d; R(A:%d+3)=R(A:%d) }", a,a,a, a,sbx,a,a);
      case OP_FORPREP:
        return String.format("FOPRP A sBx   R(A:%d)-=R(A:%d+2); pc+=sBx:%d", a,a,sbx);
      case OP_TFORLOOP:
        return String.format("TFRLP A sBx   if R(A:%d+1) ~= nil then { R(A:%d)=R(A:%d+1); pc += sBx:%d }", a,a,a,sbx);
      case OP_SETLIST:
        return String.format("SEIST A B C   R(A:%d)[(C:%d-1)*FPF+i] := R(A:%d+i), 1 <= i <= B:%d", a,c,a,b);
      case OP_CLOSURE:
        return String.format("CLSRE A Bx    R(A:%d) := closure(KPROTO[Bx:%d])", a,bx);
      case OP_VARARG:
        return String.format("VARAG A B     R(A:%d), R(A:%d+1), ..., R(A:%d+B:%d-1) = vararg", a,a,a,b);

      case OP_GETGLOBAL:
        return String.format("GTGBL A Bx    R(A:%d) := ENV(CONST(Bx:%d))", a,bx);
      case OP_SETGLOBAL:
        return String.format("STGBL A Bx    ENV(CONST(Bx:%d)) = R(A:%d)", bx, a);
      case OP_CLOSE:
        return String.format("CLOSE A       close all upvalues >= R[A:%d]", a);

    }
    return "UNKNOW";
  }


  /**
   * @see ClassMaker#vPrintStack(int...)
   */
  public static void printLuaStack(Coroutine coroutine, LuaCallFrame f, int ...i) {
    Object[] s = coroutine.objectStack;
    StringBuilder out = new StringBuilder(BUF_SIZE);
    out.append("STACK(").append(s.length).append(") [L");
    out.append(f.localBase).append(" R").append(f.returnBase).append("]");
    Tool.objectArray2String(out, s, f.localBase, selectInt(i));
    Tool.pl(out, "\n");
  }


  /**
   * @see ClassMaker#vPrintConsts(int...)
   */
  public static void printLuaConsts(Prototype p, int ...i) {
    Object[] s = p.constants;
    StringBuilder out = new StringBuilder(BUF_SIZE);
    out.append("CONSTS(").append(s.length).append(')');
    Tool.objectArray2String(out, s, 0, selectInt(i));
    Tool.pl(out, "\n");
  }


  /**
   * @see ClassMaker#vPrintOldClosureUpvaleu(int...)
   */
  public static void printUpValues(UpValue[] ups, int ...i) {
    StringBuilder out = new StringBuilder(BUF_SIZE);
    out.append("UPVALUE(").append(ups.length).append(')');
    new Array2String(out, ups, 0, selectInt(i), new UpValueRender()).render();
    Tool.pl(out, "\n");
  }


  static class UpValueRender implements Array2String.Stringify<UpValue> {

    public void item(StringBuilder out, UpValue u) {
      out.append('@').append(System.identityHashCode(u));
      out.append(SP).append(u.getIndex());

      if (u.getValue() == null) {
        out.append(" nil");
        return;
      }

      String hash = Tool.hash(u.getValue());
      String cname = u.getValue().getClass().getName();
      String strs = cname +"@"+ hash;

      out.append(SP).append(strs);

      String str = u.getValue().toString();
      if (! strs.equals(str)) {
        out.append(SP)
          .append('"')
          .append(str)
          .append('"');
      }
    }
  }


  private static ISelect selectInt(int ...is) {
    return i -> {
      for (int x : is) {
        if (x == i) return true;
      }
      return false;
    };
  }

}
