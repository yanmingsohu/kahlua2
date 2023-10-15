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

package se.krka.kahlua.vm;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Date;

import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureVisitor;
import se.krka.kahlua.stdlib.BaseLib;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.*;

/**
 * KahluaThread is modified into an inheritable class,
 * And the `luaMainloop` method is changed to protected method without final.
 */
public class KahluaThread2 extends KahluaThread {

  public final static LuaClassLoader lcl = new LuaClassLoader();
  private final Platform platform;

  private final static String[] opNames = {
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



  public KahluaThread2(Platform platform, KahluaTable environment) {
    super(platform, environment);
    this.platform = platform;
  }


  public KahluaThread2(PrintStream s, Platform p, KahluaTable e) {
    super(s, p, e);
    this.platform = p;
  }


  protected void luaMainloop() {
    try {
      LuaBuilder luab = new LuaBuilder(currentCoroutine);
      luab.makeJavacode();
      LuaScript x = luab.createJavaAgent();
      x.reinit(this, currentCoroutine);
      x.run();

    } catch(Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * @link https://the-ravi-programming-language.readthedocs.io/en/latest/lua_bytecode_reference.html
   */
  public class LuaBuilder {
    private final Class O = Object.class;
    private final Class S = String.class;
    private final Class I = int.class;
    private final Class F = float.class;
    private final Class D = double.class;
    private final Class PT = Prototype.class;
    private final Class FR = LuaCallFrame.class;

    private LuaCallFrame callFrame;
    private LuaClosure closure;
    private Prototype prototype;
    private ClassMaker cm;
    final String classPath;
    final String className;
    private MethodVisitor mv;

    Label[] labels;
    Label label;
    int pc;
    int opcode;
    int op;
    int line;

    // TMP remove
    String meta_ops[];
    int[] opcodes;
    int returnBase;
    Coroutine rootCoroutine;


    public LuaBuilder(Coroutine c) {
      callFrame = c.currentCallFrame();
      closure = callFrame.closure;
      prototype = closure.prototype;
      classPath = prototype.name;
      className = formatClassName(classPath);
      cm = new ClassMaker(className);
    }

    public void makeJavacode() {
      cm.defaultInit();
      mv = cm.beginMethod("run");

      int[] opcodes = prototype.code;
      this.opcodes = opcodes;
      labels = new Label[opcodes.length];

      for (int i=0; i<opcodes.length; ++i) {
        labels[i] = new Label();
      }

      while (callFrame.pc < opcodes.length) {
        pc = callFrame.pc++;
        op = opcodes[pc];
        opcode = op & 0x3F;
        line = prototype.lines[pc];
        label = labels[pc];

        mv.visitLabel(label);
        mv.visitLineNumber(line, label);

        Tool.pl("LL ",pc, op, opNames[opcode], line);

        switch (opcode) {
          case OP_MOVE: op_move(); break;
          case OP_LOADK: op_loadk(); break;
          case OP_LOADBOOL: op_loadbool(); break;
          case OP_LOADNIL: op_loadnil(); break;
          case OP_GETUPVAL: op_getupval(); break;
          case OP_GETGLOBAL: op_getglobal(); break;
          case OP_GETTABLE: op_gettable(); break;
          case OP_SETGLOBAL: op_setglobal(); break;
          case OP_SETUPVAL: op_setupval(); break;
          case OP_SETTABLE: op_settable(); break;
          case OP_NEWTABLE: op_newtable(); break;
          case OP_SELF: op_self(); break;
          case OP_ADD: op_add(); break;
          case OP_SUB: op_sub(); break;
          case OP_MUL: op_mul(); break;
          case OP_DIV: op_div(); break;
          case OP_MOD: op_mod(); break;
          case OP_POW: op_pow(); break;
          case OP_UNM: op_unm(); break;
          case OP_NOT: op_not(); break;
          case OP_LEN: op_len(); break;
          case OP_CONCAT: op_concat(); break;
          case OP_JMP: op_jmp(); break;
          case OP_EQ: op_eq(); break;
          case OP_LT: break;
          case OP_LE: break;
          case OP_TEST: op_test(); break;
          case OP_TESTSET: op_testset(); break;
          case OP_CALL: op_call(); break;
          case OP_TAILCALL: op_tailcall(); break;
          case OP_RETURN: op_return(); break;
          case OP_FORLOOP: op_forprep(); break;
          case OP_FORPREP: op_forloop(); break;
          case OP_TFORLOOP: op_tforloop(); break;
          case OP_SETLIST: op_setlist(); break;
          case OP_CLOSE: op_close(); break;
          case OP_CLOSURE: op_closure(); break;
          case OP_VARARG: op_vararg(); break;
        }
      }

      cm.endMethod();
    }

    public LuaScript createJavaAgent() throws InvocationTargetException,
      NoSuchMethodException,  InstantiationException,  IllegalAccessException {
      return cm.newInstance();
    }



    void op_move() {
      int a = getA8(op);
      int b = getB9(op);

      //callFrame.set(a, callFrame.get(b));
      cm.vField("callFrame");
      cm.vInt(a);
      cm.vField("callFrame");
      cm.vInt(b);
      cm.vInvokeFieldFunc("callFrame","get", int.class);
      cm.vInvokeFieldFunc("callFrame","set", int.class, O);
    }

    void op_loadk() {
      int a = getA8(op);
      int b = getBx(op);

      //callFrame.set(a, prototype.constants[b]);

      cm.vField("callFrame");
      cm.vInt(a);
      cm.vField("prototype");
      cm.vField(Prototype.class, "constants");
      cm.vInt(b);
      mv.visitInsn(AALOAD);
      cm.vInvokeFieldFunc("callFrame", "set", I, O);
    }

    // TODO: op is changed on running ???
    void op_loadbool() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);
      String v = (b == 0 ? "FALSE" : "TRUE");

      //callFrame.set(88, Boolean.FALSE);
      cm.vField("callFrame");
      cm.vInt(a);
      cm.vStatic(cm.getField(Boolean.class, v));
      cm.vInvokeFieldFunc("callFrame", "set", I, O);

      if (c != 0) {
        cm.vGoto(labels[pc + 1]);
      }
    }

    void op_loadnil() {
      int a = getA8(op);
      int b = getB9(op);

      //callFrame.stackClear(a, b);
      cm.vField("callFrame");
      mv.visitLdcInsn(a);
      mv.visitLdcInsn(b);
      cm.vInvokeFieldFunc("callFrame", "stackClear", I, I);
    }

    void op_getupval() {
      int a = getA8(op);
      int b = getB9(op);

      //callFrame.set(a, closure.upvalues[b].getValue());
      cm.vField("callFrame");
      cm.vInt(a);

      cm.vField("closure");
      cm.vField(LuaClosure.class, "upvalues");
      mv.visitLdcInsn(b);
      mv.visitInsn(AALOAD);

      cm.vInvokeFunc(UpValue.class, "getValue");
      cm.vInvokeFieldFunc("callFrame", "set", I, O);
    }

    void op_getglobal() {
      int a = getA8(op);
      int b = getBx(op);

      //Object res = tableGet(closure.env, prototype.constants[b]);
      //callFrame.set(a, res);

      cm.vField("callFrame");
      cm.vInt(a);
      cm.vThis();

      cm.vField("closure");
      cm.vField(LuaClosure.class, "env");

      cm.vField("prototype");
      cm.vField(Prototype.class, "constants");
      cm.vInt(b);
      mv.visitInsn(AALOAD);

      cm.vInvokeFunc(LuaScript.class, "tableGet", O, O);
      cm.vInvokeFieldFunc("callFrame", "set", I, O);
    }

    void op_gettable() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

      cm.vField("callFrame");
      cm.vInt(a);
      cm.vThis();

      cm.vField("callFrame");
      cm.vInt(b);
      //Object bObj = callFrame.get(b);
      cm.vInvokeFieldFunc("callFrame","get", I);

      cm.vThis();
      cm.vField("callFrame");
      cm.vInt(c);
      cm.vField("prototype");
      //Object key = getRegisterOrConstant(callFrame, c, prototype);
      cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);

      //Object res = tableGet(bObj, key);
      cm.vInvokeFunc(LuaScript.class, "tableGet", O, O);

      //callFrame.set(a, res);
      cm.vInvokeFieldFunc("callFrame", "set", I, O);
    }

    void op_setglobal() {
      int a = getA8(op);
      int b = getBx(op);

      //Object value = callFrame.get(a);
      //Object key = prototype.constants[b];
      //tableSet(closure.env, key, value);
      cm.vThis();

      cm.vField("closure");
      cm.vField(LuaClosure.class, "env");

      cm.vField("prototype");
      cm.vField(Prototype.class, "constants");
      cm.vInt(b);
      mv.visitInsn(AALOAD);

      cm.vField("callFrame");
      cm.vInt(a);
      cm.vInvokeFieldFunc("callFrame","get", I);
      cm.vInvokeFunc(LuaScript.class, "tableSet", O, O, O);
    }

    void op_setupval() {
      int a = getA8(op);
      int b = getB9(op);

      //UpValue uv = closure.upvalues[b];
      //uv.setValue(callFrame.get(a));
      cm.vField("closure");
      cm.vField(LuaClosure.class, "upvalues");
      mv.visitLdcInsn(b);
      mv.visitInsn(AALOAD);

      cm.vField("callFrame");
      cm.vInt(a);
      cm.vInvokeFieldFunc("callFrame","get", I);

      cm.vInvokeFunc(UpValue.class, "setValue", O);
    }

    void op_settable() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

      //Object aObj = callFrame.get(a);
      //Object key = getRegisterOrConstant(callFrame, b, prototype);
      //Object value = getRegisterOrConstant(callFrame, c, prototype);
      //tableSet(aObj, key, value);
      cm.vThis();

      cm.vField("callFrame");
      cm.vInt(a);
      cm.vInvokeFieldFunc("callFrame","get", I);

      cm.vThis();
      cm.vField("callFrame");
      cm.vInt(b);
      cm.vField("prototype");
      cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);

      cm.vThis();
      cm.vField("callFrame");
      cm.vInt(c);
      cm.vField("prototype");
      cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);

      cm.vInvokeFunc(LuaScript.class, "tableSet", O, O, O);
    }

    void op_newtable() {
      int a = getA8(op);

      //KahluaTable t = platform.newTable();
      //callFrame.set(a, t);
      cm.vField("callFrame");
      cm.vInt(a);
      cm.vField("platform");
      cm.vInvokeFieldFunc("platform","newTable");
      cm.vInvokeFieldFunc("callFrame", "set", I, O);
    }

    void op_self() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

      /*
      Object bObj = callFrame.get(b);
      callFrame.set(a + 1, bObj);
      Object key = getRegisterOrConstant(callFrame, c, prototype);
      Object fun = tableGet(bObj, key);
      callFrame.set(a, fun); */

      cm.vField("callFrame");
      {
        cm.vField("callFrame");
        cm.vInt(b);
        cm.vInvokeFieldFunc("callFrame", "get", I);
        mv.visitVarInsn(ASTORE, 1);
      }
      cm.vInt(a+1);
      mv.visitVarInsn(ALOAD, 1);
      cm.vInvokeFieldFunc("callFrame", "set", I, O);

      cm.vField("callFrame");
      cm.vInt(a);
      {
        cm.vThis();
        mv.visitVarInsn(ALOAD, 1);
        {
          cm.vThis();
          cm.vField("callFrame");
          cm.vInt(c);
          cm.vField("prototype");
          cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);
        }
        cm.vInvokeFunc(LuaScript.class, "tableGet", O, O);
      }
      cm.vInvokeFieldFunc("callFrame", "set", I, O);
    }

    void op_add() {
      math_cal("__add", true, ()->{
        mv.visitInsn(DADD);
      });
    }

    void op_sub() {
      math_cal("__sub", true, ()->{
        mv.visitInsn(DSUB);
      });
    }

    void op_mul() {
      math_cal("__mul", true, ()->{
        mv.visitInsn(DMUL);
      });
    }

    void op_div() {
      math_cal("__div", true, ()->{
        mv.visitInsn(DDIV);
      });
    }

    void op_pow() {
      math_cal("__pow", false, ()->{
        cm.vField("platform");
        mv.visitVarInsn(ALOAD, 3);
        cm.vInvokeFunc(Double.class, "doubleValue");
        mv.visitVarInsn(ALOAD, 4);
        cm.vInvokeFunc(Double.class, "doubleValue");
        cm.vInvokeFieldFunc("platform", "pow", D, D);
      });
    }

    void op_mod() {
      math_cal("__mod", false, ()->{
        Label v2iszero = new Label();
        Label end = new Label();

        mv.visitVarInsn(ALOAD, 4);
        cm.vInvokeFunc(Double.class, "doubleValue");
        cm.vDouble(0);
        mv.visitInsn(DCMPL);
        mv.visitJumpInsn(IFEQ, v2iszero); // if v4 == 0 goto v2iszero

        // v4 != 0
        mv.visitVarInsn(ALOAD, 3);
        cm.vInvokeFunc(Double.class, "doubleValue");
        {
          mv.visitVarInsn(ALOAD, 3);
          cm.vInvokeFunc(Double.class, "doubleValue");
          mv.visitVarInsn(ALOAD, 4);
          cm.vInvokeFunc(Double.class, "doubleValue");
          mv.visitInsn(DDIV);
          mv.visitInsn(D2I);
        }
        mv.visitInsn(I2D);
        mv.visitVarInsn(ALOAD, 4);
        cm.vInvokeFunc(Double.class, "doubleValue");
        mv.visitInsn(DMUL);
        mv.visitInsn(DSUB);

        cm.vGoto(end);

        // v4 == 0
        cm.vLabel(v2iszero, line);
        cm.vDouble(Double.NaN);

        cm.vLabel(end, line);
      });
    }

    // add sub mul div mod pow
    void math_cal(String meta_op, boolean popValued, Runnable primitiveOp) {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

      /*
      Object bo = getRegisterOrConstant(callFrame, b, prototype);
      Object co = getRegisterOrConstant(callFrame, c, prototype);

      Double bd = KahluaUtil.rawTonumber(bo);
      Double cd = KahluaUtil.rawTonumber(co);
      Object res = null;

      if (bd == null || cd == null) {
        res = metaOp(bo, co, meta_op);
      } else {
        res = bd (+) cd;
      }
      callFrame.set(a, res);*/

      Label saveRes = new Label();
      Label useMetaOp1 = new Label();
      Label useMetaOp2 = new Label();
      Label primitive = new Label();

      {
        cm.vThis();
        cm.vField("callFrame");
        cm.vInt(b);
        cm.vField("prototype");
        cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);
        mv.visitVarInsn(ASTORE, 1); // bo:1

        cm.vThis();
        cm.vField("callFrame");
        cm.vInt(c);
        cm.vField("prototype");
        cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);
        mv.visitVarInsn(ASTORE, 2); // co:2

        cm.vThis();
        mv.visitVarInsn(ALOAD, 1);
        cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
        mv.visitVarInsn(ASTORE, 3); // bd:3

        cm.vThis();
        mv.visitVarInsn(ALOAD, 2);
        cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
        mv.visitVarInsn(ASTORE, 4); // cd:4

        // if (bd == null || cd == null) then useMetaOp();
        mv.visitVarInsn(ALOAD, 3);
        mv.visitJumpInsn(IFNULL, useMetaOp2);
        mv.visitVarInsn(ALOAD, 4);
        mv.visitJumpInsn(IFNULL, useMetaOp2);
        cm.vGoto(primitive);

        // primitiveMath();
        cm.vLabel(primitive, line);

        if (popValued) {
          mv.visitVarInsn(ALOAD, 3);
          cm.vInvokeFunc(Double.class, "doubleValue");
          mv.visitVarInsn(ALOAD, 4);
          cm.vInvokeFunc(Double.class, "doubleValue");
        }

        primitiveOp.run();

        cm.vInvokeStatic(Double.class, "valueOf", D); // Must TO Double(Object)
        mv.visitVarInsn(ASTORE, 5);
        cm.vGoto(saveRes);

        // useMetaOp()
        cm.vLabel(useMetaOp1, line);
        mv.visitInsn(POP);

        cm.vLabel(useMetaOp2, line);
        cm.vThis();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitLdcInsn(meta_op);
        cm.vInvokeFunc(LuaScript.class, "metaOp", O, O, S);
        mv.visitVarInsn(ASTORE, 5);
        cm.vGoto(saveRes);
      }
      // saveRes()
      cm.vLabel(saveRes, line);
      cm.vField("callFrame");
      cm.vInt(a);
      mv.visitVarInsn(ALOAD, 5);
      cm.vInvokeFieldFunc("callFrame", "set", I, O);
    }

    void op_unm() {
      int a = getA8(op);
      int b = getB9(op);

      /*
      Object aObj = callFrame.get(b);
      Double aDouble = KahluaUtil.rawTonumber(aObj);
      Object res;
      if (aDouble != null) {
        res = KahluaUtil.toDouble(-KahluaUtil.fromDouble(aDouble));
      } else {
        Object metafun = getMetaOp(aObj, "__unm");
        //BaseLib.luaAssert(metafun != null, "__unm not defined for operand");
        res = call(metafun, aObj, null, null);
      }
      callFrame.set(a, res);*/
      Label save = new Label();
      Label useDouble = new Label();

      cm.vField("callFrame");
      cm.vInt(b);
      cm.vInvokeFieldFunc("callFrame", "get", I);
      mv.visitVarInsn(ASTORE, 1);

      cm.vThis();
      mv.visitVarInsn(ALOAD, 1);
      cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
      mv.visitVarInsn(ASTORE, 2);

      mv.visitVarInsn(ALOAD, 2);
      mv.visitJumpInsn(IFNULL, useDouble);

      // use meta op
      cm.vThis();
      mv.visitVarInsn(ALOAD, 1);
      cm.vString("__unm");
      cm.vInvokeFunc(LuaScript.class, "getMetaOp", O, S);
      mv.visitVarInsn(ASTORE, 3);
      cm.vGoto(save);

      // use double
      cm.vLabel(useDouble, line);
      mv.visitVarInsn(ALOAD, 2);
      cm.vInvokeFunc(Double.class, "doubleValue");
      mv.visitInsn(DNEG);
      cm.vInvokeStatic(Double.class, "valueOf", D);
      mv.visitVarInsn(ASTORE, 3);

      // save
      cm.vLabel(save, line);
      cm.vField("callFrame");
      cm.vInt(a);
      mv.visitVarInsn(ALOAD, 3);
      cm.vInvokeFieldFunc("callFrame", "set", I, O);
    }

    void op_not() {
      int a = getA8(op);
      int b = getB9(op);
      Object aObj = callFrame.get(b);
      callFrame.set(a, KahluaUtil.toBoolean(!KahluaUtil.boolEval(aObj)));
    }

    void op_len() {
      int a = getA8(op);
      int b = getB9(op);

      Object o = callFrame.get(b);
      Object res;
      if (o instanceof KahluaTable) {
        KahluaTable t = (KahluaTable) o;
        res = KahluaUtil.toDouble(t.len());
      } else if (o instanceof String) {
        String s = (String) o;
        res = KahluaUtil.toDouble(s.length());
      } else {
        Object f = getMetaOp(o, "__len");
        KahluaUtil.luaAssert(f != null, "__len not defined for operand");
        res = call(f, o, null, null);
      }
      callFrame.set(a, res);
    }

    void op_concat() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

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

    void op_jmp() {
      callFrame.pc += getSBx(op);
    }

    // eq lt le
    void op_eq() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

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

          String meta_op = meta_ops[opcode];

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

    void op_test() {
      int a = getA8(op);
      // b = getB9(op);
      int c = getC9(op);

      Object value = callFrame.get(a);
      if (KahluaUtil.boolEval(value) == (c == 0)) {
        callFrame.pc++;
      }
    }

    void op_testset() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

      Object value = callFrame.get(b);
      if (KahluaUtil.boolEval(value) != (c == 0)) {
        callFrame.set(a, value);
      } else {
        callFrame.pc++;
      }
    }

    void op_call() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);
      int nArguments2 = b - 1;
      if (nArguments2 != -1) {
        callFrame.setTop(a + nArguments2 + 1);
      } else {
        nArguments2 = callFrame.getTop() - a - 1;
      }

      callFrame.restoreTop = c != 0;

      int base = callFrame.localBase;

      int localBase2 = base + a + 1;
      int returnBase2 = base + a;

      Object funObject = callFrame.get(a);
      KahluaUtil.luaAssert(funObject != null, "Tried to call nil");
      Object fun = prepareMetatableCall(funObject);
      if (fun == null) {
        KahluaUtil.fail(("Object " + funObject + " did not have __call metatable set"));
      }

      // If it's a metatable __call, prepend the caller as the
      // first argument
      if (fun != funObject) {
        localBase2 = returnBase2;
        nArguments2++;
      }

      if (fun instanceof LuaClosure) {
        LuaCallFrame newCallFrame = currentCoroutine
          .pushNewCallFrame((LuaClosure) fun, null, localBase2,
            returnBase2, nArguments2, true,
            callFrame.canYield);
        newCallFrame.init();

        callFrame = newCallFrame;
        closure = newCallFrame.closure;
        prototype = closure.prototype;
        opcodes = prototype.code;
        returnBase = callFrame.returnBase;
      } else if (fun instanceof JavaFunction) {
        callJava((JavaFunction) fun, localBase2, returnBase2,
          nArguments2);

        callFrame = currentCoroutine.currentCallFrame();

        // This means that we got back from a yield to a java
        // function, such as pcall
        if (callFrame == null || callFrame.isJava()) {
          return;
        }

        closure = callFrame.closure;
        prototype = closure.prototype;
        opcodes = prototype.code;
        returnBase = callFrame.returnBase;

        if (callFrame.restoreTop) {
          callFrame.setTop(prototype.maxStacksize);
        }
      } else {
        throw new RuntimeException(
          "Tried to call a non-function: " + fun);
      }
    }

    void op_tailcall() {
      int base = callFrame.localBase;

      currentCoroutine.closeUpvalues(base);

      int a = getA8(op);
      int b = getB9(op);
      int nArguments2 = b - 1;
      if (nArguments2 == -1) {
        nArguments2 = callFrame.getTop() - a - 1;
      }

      callFrame.restoreTop = false;

      Object funObject = callFrame.get(a);
      KahluaUtil.luaAssert(funObject != null, "Tried to call nil");
      Object fun = prepareMetatableCall(funObject);
      if (fun == null) {
        KahluaUtil.fail(("Object " + funObject + " did not have __call metatable set"));
      }

      int localBase2 = returnBase + 1;

      // If it's a metatable __call, prepend the caller as the
      // first argument
      if (fun != funObject) {
        localBase2 = returnBase;
        nArguments2++;
      }

      currentCoroutine.stackCopy(base + a, returnBase,
        nArguments2 + 1);
      currentCoroutine.setTop(returnBase + nArguments2 + 1);

      if (fun instanceof LuaClosure) {
        callFrame.localBase = localBase2;
        callFrame.nArguments = nArguments2;
        callFrame.closure = (LuaClosure) fun;
        callFrame.init();
      } else {
        if (!(fun instanceof JavaFunction)) {
          KahluaUtil.fail(("Tried to call a non-function: " + fun));
        }
        Coroutine oldCoroutine = currentCoroutine;
        callJava((JavaFunction) fun, localBase2, returnBase,
          nArguments2);

        callFrame = currentCoroutine.currentCallFrame();
        oldCoroutine.popCallFrame();

        if (oldCoroutine != currentCoroutine) {
          if (oldCoroutine.isDead()) {
            if (oldCoroutine == rootCoroutine) {
              // do something clever here
            } else if (currentCoroutine.getParent() == oldCoroutine) {
              currentCoroutine.resume(oldCoroutine.getParent());
              oldCoroutine.destroy();

              // This is an implicit yield, so push a TRUE
              // to the parent
              currentCoroutine.getParent().currentCallFrame()
                .push(Boolean.TRUE);
            }
          }

          callFrame = currentCoroutine.currentCallFrame();
          if (callFrame.isJava()) {
            return;
          }
        } else {
          if (!callFrame.fromLua) {
            return;
          }
          callFrame = currentCoroutine.currentCallFrame();

          if (callFrame.restoreTop) {
            callFrame
              .setTop(callFrame.closure.prototype.maxStacksize);
          }
        }
      }

      closure = callFrame.closure;
      prototype = closure.prototype;
      opcodes = prototype.code;
      returnBase = callFrame.returnBase;
    }

    void op_return() {
      int a = getA8(op);
      int b = getB9(op) - 1;

      int base = callFrame.localBase;
      currentCoroutine.closeUpvalues(base);

      if (b == -1) {
        b = callFrame.getTop() - a;
      }

      currentCoroutine.stackCopy(callFrame.localBase + a,
        returnBase, b);
      currentCoroutine.setTop(returnBase + b);

      if (callFrame.fromLua) {
        if (callFrame.canYield
          && currentCoroutine.atBottom()) {
          callFrame.localBase = callFrame.returnBase;
          Coroutine coroutine = currentCoroutine;
          Coroutine.yieldHelper(callFrame, callFrame, b);
          coroutine.popCallFrame();

          // If this coroutine is called from a java function,
          // return immediately
          callFrame = currentCoroutine.currentCallFrame();
          if (callFrame == null || callFrame.isJava()) {
            return;
          }
        } else {
          currentCoroutine.popCallFrame();
        }
        callFrame = currentCoroutine.currentCallFrame();

        closure = callFrame.closure;
        prototype = closure.prototype;
        opcodes = prototype.code;
        returnBase = callFrame.returnBase;

        if (callFrame.restoreTop) {
          callFrame.setTop(prototype.maxStacksize);
        }
      } else {
        currentCoroutine.popCallFrame();
        return;
      }
    }

    void op_forprep() {
      int a = getA8(op);
      int b = getSBx(op);

      double iter = KahluaUtil.fromDouble(callFrame.get(a));
      double step = KahluaUtil.fromDouble(callFrame.get(a + 2));
      callFrame.set(a, KahluaUtil.toDouble(iter - step));
      callFrame.pc += b;
    }

    void op_forloop() {
      int a = getA8(op);

      double iter = KahluaUtil.fromDouble(callFrame.get(a));
      double end = KahluaUtil.fromDouble(callFrame.get(a + 1));
      double step = KahluaUtil.fromDouble(callFrame.get(a + 2));
      iter += step;
      Double iterDouble = KahluaUtil.toDouble(iter);
      callFrame.set(a, iterDouble);

      if ((step > 0) ? iter <= end : iter >= end) {
        int b = getSBx(op);
        callFrame.pc += b;
        callFrame.set(a + 3, iterDouble);
      } else {
        callFrame.clearFromIndex(a);
      }
    }

    void op_tforloop() {
      int a = getA8(op);
      int c = getC9(op);

      callFrame.setTop(a + 6);
      callFrame.stackCopy(a, a + 3, 3);
      call(2);
      callFrame.clearFromIndex(a + 3 + c);
      callFrame.setPrototypeStacksize();

      Object aObj3 = callFrame.get(a + 3);
      if (aObj3 != null) {
        callFrame.set(a + 2, aObj3);
      } else {
        callFrame.pc++;
      }
    }

    void op_setlist() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

      if (b == 0) {
        b = callFrame.getTop() - a - 1;
      }

      if (c == 0) {
        c = opcodes[callFrame.pc++];
      }

      int offset = (c - 1) * FIELDS_PER_FLUSH;

      KahluaTable t = (KahluaTable) callFrame.get(a);
      for (int i = 1; i <= b; i++) {
        Object key = KahluaUtil.toDouble(offset + i);
        Object value = callFrame.get(a + i);
        t.rawset(key, value);
      }
    }

    void op_close() {
      int a = getA8(op);
      callFrame.closeUpvalues(a);
    }

    void op_closure() {
      int a = getA8(op);
      int b = getBx(op);
      Prototype newPrototype = prototype.prototypes[b];
      LuaClosure newClosure = new LuaClosure(newPrototype,
        closure.env);
      callFrame.set(a, newClosure);
      int numUpvalues = newPrototype.numUpvalues;
      for (int i = 0; i < numUpvalues; i++) {
        op = opcodes[callFrame.pc++];
        opcode = op & 63;
        b = getB9(op);
        switch (opcode) {
          case OP_MOVE: {
            newClosure.upvalues[i] = callFrame.findUpvalue(b);
            break;
          }
          case OP_GETUPVAL: {
            newClosure.upvalues[i] = closure.upvalues[b];
            break;
          }
          default:
            // should never happen
        }
      }
    }

    void op_vararg() {
      int a = getA8(op);
      int b = getB9(op) - 1;

      callFrame.pushVarargs(a, b);
    }
  }


  public static abstract class LuaScript implements Runnable {
    protected LuaCallFrame callFrame;
    protected LuaClosure closure;
    protected Prototype prototype;
    protected Platform platform;
    protected KahluaThread2 t;

    public LuaScript() {
    }

    public void reinit(KahluaThread2 kt2, Coroutine c) {
      this.t = kt2;
      callFrame = c.currentCallFrame();
      closure = callFrame.closure;
      prototype = closure.prototype;
      platform = kt2.platform;
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

    Double rawTonumber(Object o) {
      return KahluaUtil.rawTonumber(o);
    }

    Object getBinMetaOp(Object a, Object b, String meta_op) {
      return t.getBinMetaOp(a, b, meta_op);
    }

    Object getMetaOp(Object a, String meta) {
      return t.getMetaOp(a, meta);
    }

    Object metaOp(Object a, Object b, String op) {
      Object metafun = getBinMetaOp(a, b, op);
      if (metafun == null) {
        fail("["+ op +"] not defined for operands");
      }
      return t.call(metafun, a, b, null);
    }
  }


  public class ClassMaker {
    ClassWriter cw;
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;
    final String className;
    final String classPath;
    final Class  superClass = LuaScript.class;
    final String superClassName = toClassPath(superClass.getName());
    Class clazz;

    /**
     * @param className '.' split class path
     */
    private ClassMaker(String className) {
      this.className = className;
      this.classPath = toClassPath(className);

      cw = new ClassWriter(COMPUTE_FRAMES);
      cw.visit(52,
        ACC_PUBLIC + Opcodes.ACC_SUPER,
        classPath,
        null,
        superClassName,
        null);

      cw.visitSource(classPath +".lua", null);
    }

    private MethodVisitor beginMethod(String mname) {
      if (mv != null) throw new RuntimeException();
      mv = cw.visitMethod(ACC_PUBLIC, mname, "()V", null, null);
      mv.visitCode();
      return mv;
    }

    private void endMethod() {
      mv.visitInsn(RETURN);
      mv.visitMaxs(10, 10);
      //mv.visitMaxs(0 ,0);
      mv.visitEnd();
      mv = null;
    }

    public Class genClass() {
      if (clazz == null) {
        cw.visitEnd();
        byte[] buf = cw.toByteArray();
        clazz = lcl.defineClass(className, buf);
        cw = null;

        try {
          FileOutputStream w = new FileOutputStream("./bin/temp-test.class");
          w.write(buf);
          w.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      return clazz;
    }

    public LuaScript newInstance() throws IllegalAccessException,
          InstantiationException, NoSuchMethodException,
          InvocationTargetException {
      Class c = genClass();
      return (LuaScript) c.getDeclaredConstructor().newInstance();
    }

    private void defaultInit() {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();

      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, superClassName, "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
      mv = null;
    }

    // Execution of methods in members
    private void vInvokeFieldFunc(String member, String method, Class ...param) {
      Field f = getField(member);
      Class t = f.getType();
      Method m = getMethod(t, method, param);

      mv.visitMethodInsn(INVOKEVIRTUAL, toClassPath(t),
          m.getName(), getMethodSi(m), false);
    }

    private void vInvokeFunc(Class owner, String method, Class ...param) {
      Method m = getMethod(owner, method, param);

      mv.visitMethodInsn(INVOKEVIRTUAL, toClassPath(owner),
        m.getName(), getMethodSi(m), false);
    }

    private void vInvokeStatic(Class owner, String method, Class ...param) {
      Method m = getMethod(owner, method, param);

      mv.visitMethodInsn(INVOKESTATIC, toClassPath(owner),
        m.getName(), getMethodSi(m), false);
    }

    private void vInt(int a) {
      mv.visitLdcInsn(a);
    }

    private void vDouble(double a) {
      mv.visitLdcInsn(a);
    }

    private void vString(String s) {
      mv.visitLdcInsn(s);
    }

    private void vThis() {
      // Zero is This
      mv.visitVarInsn(ALOAD, 0);
    }

    private void vField(String fname) {
      vThis();
      vField(superClass, fname);
    }

    private void vField(Class owner, String fname) {
      String oclass = toClassPath(owner.getName());

      Field f = getField(owner, fname);
      StringBuilder desc = new StringBuilder();
      typeName(desc, f.getType());

      mv.visitFieldInsn(GETFIELD, oclass, f.getName(), desc.toString());
    }

    private void vStatic(Field f) {
      Class c = f.getDeclaringClass();
      String oclass = toClassPath(c.getName());
      String v = f.getName();

      StringBuilder desc = new StringBuilder();
      typeName(desc, f.getType());

      mv.visitFieldInsn(GETSTATIC, oclass, v, desc.toString());
    }

    private void vStatic(Method m) {
      Class c = m.getDeclaringClass();
      String oclass = toClassPath(c.getName());
      String v = m.getName();

      StringBuilder desc = new StringBuilder();
      typeName(desc, c);

      mv.visitFieldInsn(GETSTATIC, oclass, v, desc.toString());
    }

    private void vGoto(Label l) {
      mv.visitJumpInsn(GOTO, l);
    }

    private void vLabel(Label l, int line) {
      mv.visitLabel(l);
      mv.visitLineNumber(line, l);
    }

    // Get field from Super Class
    private Field getField(String n) {
      return getField(superClass, n);
    }

    private Field getField(Class c, String n) {
      try {
        return c.getDeclaredField(n);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private Method getMethod(Class c, String m, Class ...param) {
      try {
        return c.getDeclaredMethod(m, param);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  }


  public static class LuaClassLoader extends ClassLoader {
    public Class defineClass(String name, byte[] code) {
      return defineClass(name, code, 0, code.length);
    }
  }


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
      Class c = (Class) t;
      if (c.isPrimitive()) {
        b.append(Character.toUpperCase(t.getTypeName().charAt(0)));
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



  public void test_every_lua_code(int endIndex) throws Exception {
    final String line = " -------------------------------------------- ";
    Prototype p = new Prototype();
    p.name = "./testsuite/lua/testhelper.lua";
    p.constants = new Object[]{};
    p.lines = new int[opNames.length];

    KahluaTable env = platform.newTable();
    LuaClosure lc = new LuaClosure(p, env);
    Coroutine cr = new Coroutine(platform, env, this);
    cr.pushNewCallFrame(lc, null, 0,0,0, false, true);
    LuaBuilder luab = new LuaBuilder(cr);
    luab.op = (1<<6) | (2<<14) | (3<<23);
    luab.labels = new Label[p.lines.length];

    luab.cm.defaultInit();
    luab.mv = luab.cm.beginMethod("run");

    for (int i=0; i<p.lines.length; ++i) {
      p.lines[i] = i;
      luab.labels[i] = new Label();
    }

    for (int i=0; i<=endIndex; ++i) {
      luab.pc = i+1;
      luab.op = ((1)<<6) | ((3)<<14) | ((2)<<23);
      Label label = luab.labels[i];
      luab.line = p.lines[i];

      Method m = LuaBuilder.class.getDeclaredMethod(opNames[i].toLowerCase());
      Tool.pl(">> ", m.getName(), "()");

      luab.mv.visitLabel(label);
      luab.mv.visitLineNumber(p.lines[i], label);
      luab.mv.visitLdcInsn(line + m.getName() +" ( "+ i +" )");
      luab.cm.vInvokeStatic(Tool.class, "pl", Object.class);
      m.invoke(luab);

      Tool.pl("> ok");
    }

    luab.mv.visitLdcInsn("END / "+ new Date().toString());
    luab.cm.vInvokeStatic(Tool.class, "pl", Object.class);
    luab.cm.endMethod();

    LuaScript agent = luab.createJavaAgent();
    agent.reinit(this, cr);
    agent.run();
  }
}
