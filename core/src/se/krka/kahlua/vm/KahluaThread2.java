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

import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import se.krka.kahlua.stdlib.BaseLib;

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
      x.call(currentCoroutine);

    } catch(Exception e) {
      e.printStackTrace();
    }
  }


  public void test() throws Exception {
    Prototype p = new Prototype();
    p.name = "./testsuite/lua/testhelper.lua";
    p.constants = new Object[]{};

    KahluaTable env = platform.newTable();
    LuaClosure lc = new LuaClosure(p, env);
    Coroutine cr = new Coroutine(platform, env, this);
    cr.pushNewCallFrame(lc, null, 0,0,0, false, true);
    LuaBuilder luab = new LuaBuilder(cr);

    luab.cm.defaultInit();
    luab.mv = luab.cm.beginMethod("run");

    for (int i=0; i<=1; ++i) {
      Method m = LuaBuilder.class.getDeclaredMethod(opNames[i].toLowerCase());
      System.out.println("> "+ m);
      m.invoke(luab);
      System.out.println("> ok");
    }

    luab.cm.endMethod();

    LuaScript agent = luab.createJavaAgent();
    agent.call(cr);
  }


  /**
   * @link https://the-ravi-programming-language.readthedocs.io/en/latest/lua_bytecode_reference.html
   */
  public class LuaBuilder {
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

        System.out.println("LL " +
            pc + " = " + op + " / " + opNames[opcode]+" "+ line);

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
          case OP_SUB: break;
          case OP_MUL: break;
          case OP_DIV: break;
          case OP_MOD: break;
          case OP_POW: break;
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

      cm._call_print();
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
      cm.vInvokeFieldFunc("callFrame","set", int.class, Object.class);
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
      cm.vInvokeFieldFunc("callFrame", "set", int.class, Object.class);
    }

    void op_loadbool() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);
      String v = (b == 0 ? "FALSE" : "TRUE");

      //callFrame.set(88, Boolean.FALSE);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "se/krka/kahlua/vm/KahluaThread2$LuaBuilder",
        "callFrame", "Lse/krka/kahlua/vm/LuaCallFrame;");
      mv.visitLdcInsn(a);
      mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
        v, "Ljava/lang/Boolean;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "se/krka/kahlua/vm/LuaCallFrame",
        "set", "(ILjava/lang/Object;)V", false);

      if (c != 0) {
        mv.visitJumpInsn(GOTO, labels[pc + 1]);
      }
    }

    void op_loadnil() {
      int a = getA8(op);
      int b = getB9(op);

      //callFrame.stackClear(a, b);
      //callFrame.stackClear(88, 99);

      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "se/krka/kahlua/vm/KahluaThread2$LuaBuilder",
        "callFrame", "Lse/krka/kahlua/vm/LuaCallFrame;");

      mv.visitLdcInsn(a);
      mv.visitLdcInsn(b);
      mv.visitMethodInsn(INVOKEVIRTUAL, "se/krka/kahlua/vm/LuaCallFrame",
        "stackClear", "(II)V", false);
    }

    void op_getupval() {
      int a = getA8(op);
      int b = getB9(op);

      //UpValue uv = closure.upvalues[b];
      //callFrame.set(a, uv.getValue());

      //UpValue uv = closure.upvalues[99];
      //callFrame.set(88, uv.getValue());

      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "se/krka/kahlua/vm/KahluaThread2$LuaBuilder", "closure", "Lse/krka/kahlua/vm/LuaClosure;");
      mv.visitFieldInsn(GETFIELD, "se/krka/kahlua/vm/LuaClosure", "upvalues", "[Lse/krka/kahlua/vm/UpValue;");
      mv.visitLdcInsn(b);
      mv.visitInsn(AALOAD);
      mv.visitVarInsn(ASTORE, 3);

      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "se/krka/kahlua/vm/KahluaThread2$LuaBuilder", "callFrame", "Lse/krka/kahlua/vm/LuaCallFrame;");
      mv.visitLdcInsn(a);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitMethodInsn(INVOKEVIRTUAL, "se/krka/kahlua/vm/UpValue", "getValue", "()Ljava/lang/Object;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "se/krka/kahlua/vm/LuaCallFrame", "set", "(ILjava/lang/Object;)V", false);
    }

    void op_getglobal() {
      int a = getA8(op);
      int b = getBx(op);
      Object res = tableGet(closure.env, prototype.constants[b]);
      callFrame.set(a, res);
    }

    void op_gettable() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

      Object bObj = callFrame.get(b);

      Object key = getRegisterOrConstant(callFrame, c, prototype);

      Object res = tableGet(bObj, key);
      callFrame.set(a, res);
    }

    void op_setglobal() {
      int a = getA8(op);
      int b = getBx(op);
      Object value = callFrame.get(a);
      Object key = prototype.constants[b];

      tableSet(closure.env, key, value);
    }

    void op_setupval() {
      int a = getA8(op);
      int b = getB9(op);

      UpValue uv = closure.upvalues[b];
      uv.setValue(callFrame.get(a));
    }

    void op_settable() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

      Object aObj = callFrame.get(a);

      Object key = getRegisterOrConstant(callFrame, b, prototype);
      Object value = getRegisterOrConstant(callFrame, c, prototype);

      tableSet(aObj, key, value);
    }

    void op_newtable() {
      int a = getA8(op);

      KahluaTable t = platform.newTable();
      callFrame.set(a, t);
    }

    void op_self() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

      Object key = getRegisterOrConstant(callFrame, c, prototype);
      Object bObj = callFrame.get(b);

      Object fun = tableGet(bObj, key);

      callFrame.set(a, fun);
      callFrame.set(a + 1, bObj);
    }

    // add sub mul div mod pow
    void op_add() {
      int a = getA8(op);
      int b = getB9(op);
      int c = getC9(op);

      Object bo = getRegisterOrConstant(callFrame, b, prototype);
      Object co = getRegisterOrConstant(callFrame, c, prototype);

      Double bd = null, cd = null;
      Object res = null;
      if ((bd = KahluaUtil.rawTonumber(bo)) == null
        || (cd = KahluaUtil.rawTonumber(co)) == null) {
        String meta_op = meta_ops[opcode];

        Object metafun = getBinMetaOp(bo, co, meta_op);
        if (metafun == null) {
          KahluaUtil.fail((meta_op + " not defined for operands"));
        }
        res = call(metafun, bo, co, null);
      } else {
        res = primitiveMath(bd, cd, opcode);
      }
      callFrame.set(a, res);
    }

    void op_unm() {
      int a = getA8(op);
      int b = getB9(op);
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
      callFrame.set(a, res);
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
    public static final String myClassPath = toClassPath(LuaScript.class.getName());
    protected LuaCallFrame callFrame;
    protected LuaClosure closure;
    protected Prototype prototype;

    public void call(Coroutine c) {
      callFrame = c.currentCallFrame();
      closure = callFrame.closure;
      prototype = closure.prototype;

      run();
    }
  }


  public static class ClassMaker {
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

      cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
      cw.visit(52,
        ACC_PUBLIC + Opcodes.ACC_SUPER,
        classPath,
        null,
        superClassName,
        null);

      cw.visitSource(classPath +".lua", null);
    }

    private MethodVisitor beginMethod(String mname) {
      mv = cw.visitMethod(ACC_PUBLIC, mname, "()V", null, null);
      mv.visitCode();
      return mv;
    }

    private void _call_print() {
      Label l0 = new Label();
      mv.visitLabel(l0);
      mv.visitLineNumber(77, l0);
      mv.visitFieldInsn(GETSTATIC,
        "java/lang/System",
        "out",
        "Ljava/io/PrintStream;");
      mv.visitLdcInsn("hello");
      mv.visitMethodInsn(INVOKEVIRTUAL,
        "java/io/PrintStream",
        "println",
        "(Ljava/lang/String;)V",
        false);

      Label l1 = new Label();
      mv.visitLabel(l1);
      mv.visitLineNumber(78, l1);
    }

    private void endMethod() {
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
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

      Label label0 = new Label();
      mv.visitLabel(label0);
      mv.visitLineNumber(0, label0);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, superClassName, "<init>", "()V", false);
      mv.visitInsn(RETURN);

      String desc = "L"+ classPath +";";
      Label label1 = new Label();
      mv.visitLabel(label1);
      mv.visitLocalVariable("this", desc, null, label0, label1, 0);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }

    // Execution of methods in members
    private void vInvokeFieldFunc(String member, String method, Class ...param) {
      Field f = getField(member);
      Class t = f.getType();
      Method m = getMethod(t, method, param);

      mv.visitMethodInsn(INVOKEVIRTUAL, toClassPath(t),
          m.getName(), getMethodSi(m), false);
    }

    private void vInt(int a) {
      mv.visitLdcInsn(a);
    }

    private void vThis() {
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
      System.out.println(oclass+" "+ f.getName() +" "+ desc.toString());
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
}
