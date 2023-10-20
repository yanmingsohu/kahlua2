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

import static se.krka.kahlua.vm2.KahluaThread2.*;


public class DebugInf {

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


  public DebugInf(ClassMaker cm, String classPath) {
    this.cm = cm;
    this.classPath = classPath;
  }


  public void update(int line, int opcode, int op, int pc) {
    this.line = line;
    this.opcode = opcode;
    this.op = op;
    this.pc = pc;
  }


  public void opArg() {
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

    cm.vPrint(msg +"\n -- "+ opDesc());

  }


  public void stack() {
    cm.vPrintLuaStack();
  }


  public void build() {
    Tool.pl("LL ",pc, Tool.num8len(op), opNames[opcode], line);
  }


  public String opDesc() {
    return opDesc(opcode);
  }


  public String opDesc(int opcode) {
    switch (opcode) {
      case OP_MOVE:
        return "MOVE A B     R(A) := R(B)";
      case OP_LOADK:
        return "LOADK A Bx    R(A) := Kst(Bx)";
      case OP_LOADBOOL:
        return "LOADBOOL A B C    R(A) := (Bool)B; if (C) pc++";
      case OP_LOADNIL:
        return "LOADNIL A B     R(A), R(A+1), ..., R(A+B) := nil";
      case OP_GETUPVAL:
        return "GETUPVAL  A B     R(A) := UpValue[B]";
      case OP_GETTABLE:
        return "GETTABLE A B C   R(A) := R(B)[RK(C)]";
      case OP_SETUPVAL:
        return "SETUPVAL  A B     UpValue[B] := R(A)";
      case OP_SETTABLE:
        return "SETTABLE A B C   R(A)[RK(B)] := RK(C)";
      case OP_NEWTABLE:
        return "NEWTABLE A B C   R(A) := {} (size = B,C)";
      case OP_SELF:
        return "SELF  A B C   R(A+1) := R(B); R(A) := R(B)[RK(C)]";
      case OP_ADD:
        return "ADD   A B C   R(A) := RK(B) + RK(C)";
      case OP_SUB:
        return "SUB   A B C   R(A) := RK(B) - RK(C)";
      case OP_MUL:
        return "MUL   A B C   R(A) := RK(B) * RK(C)";
      case OP_DIV:
        return "DIV   A B C   R(A) := RK(B) / RK(C)";
      case OP_MOD:
        return "MOD   A B C   R(A) := RK(B) % RK(C)";
      case OP_POW:
        return "POW   A B C   R(A) := RK(B) ^ RK(C)";
      case OP_UNM:
        return "UNM   A B     R(A) := -R(B)";
      case OP_NOT:
        return "NOT   A B     R(A) := not R(B)";
      case OP_LEN:
        return "LEN A B     R(A) := length of R(B)";
      case OP_CONCAT:
        return "CONCAT A B C   R(A) := R(B).. ... ..R(C)";
      case OP_JMP:
        return "JMP A sBx   pc+=sBx; if (A) close all upvalues >= R(A - 1)";
      case OP_EQ:
        return "EQ  A B C if ((RK(B) == RK(C)) ~= A) then PC++";
      case OP_LT:
        return "LT  A B C if ((RK(B) <  RK(C)) ~= A) then PC++";
      case OP_LE:
        return "LE  A B C if ((RK(B) <= RK(C)) ~= A) then PC++";
      case OP_TEST:
        return "TEST        A C     if (boolean(R(A)) != C) then PC++";
      case OP_TESTSET:
        return "TESTSET     A B C   if (boolean(R(B)) != C) then PC++ else R(A) := R(B)";
      case OP_CALL:
        return "CALL A B C    R(A), ... ,R(A+C-2) := R(A)(R(A+1), ... ,R(A+B-1))";
      case OP_TAILCALL:
        return "TAILCALL  A B C return R(A)(R(A+1), ... ,R(A+B-1))";
      case OP_RETURN:
        return "RETURN  A B return R(A), ... ,R(A+B-2)";
      case OP_FORLOOP:
        return "FORLOOP    A sBx   R(A)+=R(A+2);\n" +
               "                   if R(A) <?= R(A+1) then { pc+=sBx; R(A+3)=R(A) }";
      case OP_FORPREP:
        return "FORPREP    A sBx   R(A)-=R(A+2); pc+=sBx";
      case OP_TFORLOOP:
        return "TFORLOOP    A sBx      if R(A+1) ~= nil then { R(A)=R(A+1); pc += sBx }";
      case OP_SETLIST:
        return "SETLIST A B C   R(A)[(C-1)*FPF+i] := R(A+i), 1 <= i <= B";
      case OP_CLOSURE:
        return "CLOSURE A Bx    R(A) := closure(KPROTO[Bx])";
      case OP_VARARG:
        return "VARARG  A B R(A), R(A+1), ..., R(A+B-1) = vararg";

      case OP_GETGLOBAL:
        return "GETGLOBAL A B   R(A) := ENV(B) || CONST(B)";
      case OP_SETGLOBAL:
        return "SETGLOBAL A B   ENV(CONST(B)) = R(A)";
        case OP_CLOSE:
        return "CLOSE close all upvalues >= R[A]";

    }
    return "UNKNOW";
  }
}
