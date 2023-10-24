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


import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import se.krka.kahlua.vm.*;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static se.krka.kahlua.vm.KahluaThread.*;


/**
 * @link https://the-ravi-programming-language.readthedocs.io/en/latest/lua_bytecode_reference.html
 */
public class LuaBuilder implements IConst {

  public final static String ROOT_FUNCTION_NAME = "run";
  public final static String CANNOT_BIND_LUA_NAME = "";

  final String classPath;
  final String className;
  protected MethodVisitor mv;
  protected final ClassMaker cm;
  protected final List<ClosureInf> plist;

  protected Label[] labels;
  protected Label label;
  protected int pc;
  protected int opcode;
  protected int op;
  protected int line;
  protected int id = 1;
  private DebugInf di;


  public LuaBuilder(DebugInf di, String _classPath, String _outDir) {
    this.classPath = _classPath;
    this.className = Tool.formatClassName(classPath);
    this.cm = new ClassMaker(className, _outDir, di);
    this.plist = new ArrayList<>();
    this.di = di;
  }


  public void makeJavacode(Prototype p) {
    final long start = System.currentTimeMillis();
    cm.defaultConstructor();
    ClosureInf root = pushClosure(p, ROOT_FUNCTION_NAME, -1, "<init>");
    newClosureFunction(root);

    if (di.has(DebugInf.BUILD)) {
      Tool.pl("Build", classPath, "used", System.currentTimeMillis() - start, "ms");
    }
  }


  private void newClosureFunction(ClosureInf ci) {
    final int startIndex = plist.size();
    mv = cm.beginMethod(ci.funcName);
    State state = new State(ci);
    cm.updateState(state);

    di.update(cm, classPath);
    int firstLine = ci.prototype.lines[0];
    cm.vLabel(state.initLabel, firstLine);
    cm.vClosureFunctionHeader(state);
    cm.vLabel(state.initOverLabel, firstLine);

    while (state.hasNext()) {
      state.readNextOp();

      if (di.flag != DebugInf.NONE) {
        debugOp();
      }

      do_op_code(opcode, state);
    }

    cm.vLabel(state.returnLabel, line);
    cm.vClosureFunctionFoot(state);
    cm.endMethod(state);
    processSubClosure(startIndex, plist.size());
  }


  private void debugOp() {
    if (di.has(DebugInf.STACK)) {
      di.stackAuto();
    }

    di.update(line, opcode, op, pc);

    if (di.has(DebugInf.FULLOP)) {
      di.fullMsg();
    } else if (di.has(DebugInf.SHORTOP)) {
      di.shortMsg();
    }

    if (di.has(DebugInf.SHORPS)) {
      di.shortPaser();
    }
  }


  public class State extends StateBase {
    final ClosureInf ci;
    final Label returnLabel;
    final Label initLabel;
    final Label initOverLabel;
    final Label cacheLabel;
    final Label finallyLabel;

    private final int[] opcodes;
    private int npc = 0;

    public final LocalVar vCallframe;
    public final LocalVar vPlatform;
    public final LocalVar vClosure;
    public final LocalVar vPrototype;
    public final LocalVar vCI;
    public final LocalVar vError;


    public State(ClosureInf ci) {
      super(mv);
      this.ci = ci;
      this.opcodes = ci.prototype.code;
      this.initLabel = new Label();
      this.initOverLabel = new Label();
      this.cacheLabel = new Label();
      this.finallyLabel = new Label();
      this.returnLabel = initLabels();

      this.vCallframe = internalVar(FR);
      this.vPlatform = internalVar(Platform.class);
      this.vClosure = internalVar(CU);
      this.vPrototype = internalVar(PT);
      this.vCI = internalVar(CI);
      this.vError = internalVar(Throwable.class);
    }


    private Label initLabels() {
      labels = new Label[opcodes.length + 1];
      for (int i=0; i<labels.length; ++i) {
        labels[i] = new Label();
      }
      return labels[ labels.length -1 ];
    }


    private LocalVar internalVar(Class c) {
      String name = Tool.toLocalVarName(c);
      final int vid = nextInternalVarId();
      LocalVar v = new LocalVar(mv, c, vid, name, initLabel, returnLabel);
      super.add(v);
      return v;
    }


    private int readNextOp() {
      pc = npc++;
      op = opcodes[pc];
      opcode = op & 0x3F;

      line = ci.prototype.lines[pc];
      label = labels[pc];

      mv.visitLabel(label);
      mv.visitLineNumber(line, label);
      return op;
    }


    private Label jumpn1() {
      return jumpToLabel(1);
    }


    private Label jumpToLabel(int i) {
      return labels[npc + i];
    }


    private boolean hasNext() {
      return npc < opcodes.length;
    }


    public LocalVar newVar(Class c, String name) {
      return super.newVar(c, name, label, labels[npc]);
    }


    public LocalVar newVar(String name) {
      return this.newVar(Object.class, name);
    }
  }


  private void processSubClosure(int start, int end) {
    for (int i=start; i<end; ++i) {
      ClosureInf ci = plist.get(i);
      newClosureFunction(ci);
    }
  }


  protected void do_op_code(int opcode, State s) {
    s.resetVarIndex();
    switch (opcode) {
      case OP_MOVE: op_move(); break;
      case OP_LOADK: op_loadk(); break;
      case OP_LOADBOOL: op_loadbool(s); break;
      case OP_LOADNIL: op_loadnil(); break;
      case OP_GETUPVAL: op_getupval(s); break;
      case OP_GETGLOBAL: op_getglobal(); break;
      case OP_GETTABLE: op_gettable(); break;
      case OP_SETGLOBAL: op_setglobal(); break;
      case OP_SETUPVAL: op_setupval(s); break;
      case OP_SETTABLE: op_settable(); break;
      case OP_NEWTABLE: op_newtable(); break;
      case OP_SELF: op_self(s); break;
      case OP_ADD: op_add(s); break;
      case OP_SUB: op_sub(s); break;
      case OP_MUL: op_mul(s); break;
      case OP_DIV: op_div(s); break;
      case OP_MOD: op_mod(s); break;
      case OP_POW: op_pow(s); break;
      case OP_UNM: op_unm(s); break;
      case OP_NOT: op_not(); break;
      case OP_LEN: op_len(s); break;
      case OP_CONCAT: op_concat(s); break;
      case OP_JMP: op_jmp(s); break;
      case OP_EQ: op_eq(s); break;
      case OP_LT: op_lt(s); break;
      case OP_LE: op_le(s); break;
      case OP_TEST: op_test(s); break;
      case OP_TESTSET: op_testset(s); break;
      case OP_CALL: op_call(s); break;
      case OP_TAILCALL: op_tailcall(s); break;
      case OP_RETURN: op_return(s); break;
      case OP_FORLOOP: op_forloop(s); break;
      case OP_FORPREP: op_forprep(s); break;
      case OP_TFORLOOP: op_tforloop(s); break;
      case OP_SETLIST: op_setlist(s); break;
      case OP_CLOSE: op_close(); break;
      case OP_CLOSURE: op_closure(s); break;
      case OP_VARARG: op_vararg(); break;
    }
  }


  public LuaScript createJavaAgent() throws InvocationTargetException,
      NoSuchMethodException, InstantiationException, IllegalAccessException {
    LuaScript ls = cm.newInstance();
    ls.setClosureInf(plist);
    return ls;
  }


  private ClosureInf pushClosure(Prototype sp, String funcName,
                                 int stackIndex, String luaName) {
    int index = plist.size();
    ClosureInf inf = new ClosureInf(sp, index, funcName, stackIndex, luaName);
    plist.add(inf);
    return inf;
  }


  private String closureFuncName(Prototype sub) {
    int begin = sub.lines[0];
    int end = sub.lines[sub.lines.length - 1];
    return "closure_"+ (id++) +"$L"+ begin +'_'+ end;
  }


  public String getOutoutFile() {
    return cm.getOutputFile();
  }


  void op_move() {
    int a = getA8(op);
    int b = getB9(op);

    cm.vSetStackVar(a, () -> {
      cm.vGetStackVar(b);
    });
  }

  void op_loadk() {
    int a = getA8(op);
    int b = getBx(op);

    cm.vSetStackVar(a, () -> {
      cm.vGetConstants(b);
    });
  }

  void op_loadbool(State s) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    String v = (b == 0 ? "FALSE" : "TRUE");

    cm.vSetStackVar(a, () -> {
      cm.vBooleanObj(b != 0);
    });

    if (c != 0) {
      cm.vGoto(s.jumpn1());
    }
  }

  void op_loadnil() {
    int a = getA8(op);
    int b = getB9(op);

    cm.vClearStack(a, b);
  }

  void op_getupval(State s) {
    int a = getA8(op);
    int b = getB9(op);

    VUpvalueOp up = new VUpvalueOp(cm, mv, b, s);
    cm.vSetStackVar(a, () -> {
      up.getValue();
    });
  }

  void op_getglobal() {
    int a = getA8(op);
    int b = getBx(op);

    cm.vSetStackVar(a, () -> {
      cm.vGetGlobalVar(() -> {
        cm.vGetConstants(b);
      });
    });
  }

  void op_setglobal() {
    final int a = getA8(op);
    final int b = getBx(op);

    cm.vSetTableVar(new IBuildParam3() {
      public void param1() {
        cm.vEnvironment();
      }
      public void param2() {
        cm.vGetConstants(b);
      }
      public void param3() {
        cm.vGetStackVar(a);
      }
    });
  }

  void op_gettable() {
    final int a = getA8(op);
    final int b = getB9(op);
    final int c = getC9(op);

    cm.vSetStackVar(a, () -> {
      cm.vGetTableVar(new IBuildParam2() {
        public void param1() {
          cm.vGetStackVar(b);
        }
        public void param2() {
          cm.vGetRegOrConst(c);
        }
      });
    });
  }

  void op_setupval(State s) {
    int a = getA8(op);
    int b = getB9(op);

    VUpvalueOp upop = new VUpvalueOp(cm, mv, b, s);
    upop.setValue(() -> {
      cm.vGetStackVar(a);
    });
  }

  void op_settable() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    final int vTmp = 0;

    cm.vSetTableVar(new IBuildParam3() {
      public void param1() {
        cm.vGetStackVar(a);
      }
      public void param2() {
        cm.vGetRegOrConst(b);
      }
      public void param3() {
        cm.vGetRegOrConst(c);
      }
    });
  }

  void op_newtable() {
    int a = getA8(op);

    cm.vSetStackVar(a, ()->{
      cm.vNewTable();
    });
  }

  void op_self(State s) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    LocalVar bObj = s.newVar("bObj");


    cm.vGetStackVar(b);
    bObj.store();

    cm.vSetStackVar(a+1, ()->{
      bObj.load();
    });

    cm.vSetStackVar(a, ()->{
      cm.vGetTableVar(new IBuildParam2() {
        public void param1() {
          bObj.load();
        }
        public void param2() {
          cm.vGetRegOrConst(c);
        }
      });
    });
  }

  void op_add(State s) {
    math_cal(s, "__add", true, (bd, cd)->{
      mv.visitInsn(DADD);
    });
  }

  void op_sub(State s) {
    math_cal(s, "__sub", true, (bd, cd)->{
      mv.visitInsn(DSUB);
    });
  }

  void op_mul(State s) {
    math_cal(s, "__mul", true, (bd, cd)->{
      mv.visitInsn(DMUL);
    });
  }

  void op_div(State s) {
    math_cal(s, "__div", true, (bd, cd)->{
      mv.visitInsn(DDIV);
    });
  }

  void op_pow(State s) {
    math_cal(s, "__pow", false, (bd, cd)->{
      s.vPlatform.load();
      bd.load();
      cm.vToPrimitiveDouble(false);
      cd.load();
      cm.vToPrimitiveDouble(false);
      cm.vInvokeInterface(Platform.class, "pow", D, D);
    });
  }

  void op_mod(State s) {
    math_cal(s, "__mod", false, (bd, cd)->{
      Label v2iszero = new Label();
      Label end = new Label();

      cd.load();
      cm.vToPrimitiveDouble(false);
      cm.vDouble(0);
      mv.visitInsn(DCMPL);
      mv.visitJumpInsn(IFEQ, v2iszero); // if v4 == 0 goto v2iszero

      // v4 != 0
      bd.load();
      cm.vToPrimitiveDouble(false);
      {
        bd.load();
        cm.vToPrimitiveDouble(false);
        cd.load();
        cm.vToPrimitiveDouble(false);
        mv.visitInsn(DDIV);
        mv.visitInsn(D2I);
      }
      mv.visitInsn(I2D);
      cd.load();
      cm.vToPrimitiveDouble(false);
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
  void math_cal(State s, String meta_op, boolean popValued, IMathOp primitiveOp) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    final LocalVar bo = s.newVar(O, "bo");
    final LocalVar co = s.newVar(O, "co");
    final LocalVar bd = s.newVar(O, "bd");
    final LocalVar cd = s.newVar(O, "cd");
    final LocalVar res = s.newVar(O, "res");

    Label saveRes = new Label();
    Label useMetaOp1 = new Label();
    Label useMetaOp2 = new Label();
    Label primitive = new Label();

    {
      cm.vGetRegOrConst(b);
      bo.store();

      cm.vGetRegOrConst(c);
      co.store();

      cm.vThis();
      bo.load();
      //TODO: optimization
      cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
      bd.store();

      cm.vThis();
      co.load();
      cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
      cd.store();

      // if (bd == null || cd == null) then useMetaOp();
      bd.load();
      mv.visitJumpInsn(IFNULL, useMetaOp2);
      cd.load();
      mv.visitJumpInsn(IFNULL, useMetaOp2);
      cm.vGoto(primitive);
    }

    // primitiveMath();
    {
      cm.vLabel(primitive, line);

      if (popValued) {
        bd.load();
        cm.vToPrimitiveDouble(false);
        cd.load();
        cm.vToPrimitiveDouble(false);
      }

      primitiveOp.calc(bd, cd);
      // Must TO Double(Object)
      cm.vToObjectDouble(false);
      res.store();
      cm.vGoto(saveRes);
    }

    // useMetaOp()
    {
      cm.vLabel(useMetaOp1, line);
      mv.visitInsn(POP);

      cm.vLabel(useMetaOp2, line);
      cm.vCallMetaOp(meta_op, new IBuildParam2() {
        public void param1() {
          bo.load();
        }
        public void param2() {
          co.load();
        }
      });
      res.store();
      cm.vGoto(saveRes);
    }

    // saveRes()
    cm.vLabel(saveRes, line);
    cm.vSetStackVar(a, ()-> res.load());
  }

  void op_unm(State s) {
    int a = getA8(op);
    int b = getB9(op);

    LocalVar aObj = s.newVar("aObj");
    LocalVar res = s.newVar("res");
    LocalVar metafun = s.newVar("metafun");

    cm.vGetStackVar(b);

    cm.vToNumber(new IToNumber() {
      public void success() {
        mv.visitInsn(DNEG);
        cm.vInvokeStatic(Double.class, "valueOf", D);
        res.store();
      }

      public void nan() {
        aObj.store();

        cm.vGetMetaOp("__unm", ()->{
          aObj.load();
        });

        metafun.store();

        cm.vCall(new IBuildParam4() {
          public void param1() {
            metafun.load();
          }
          public void param2() {
            aObj.load();
          }
          public void param3() {
            cm.vNull();
          }
          public void param4() {
            cm.vNull();
          }
        });
        res.store();
      }
    });

    cm.vSetStackVar(a, ()->{
      res.load();
    });
  }

  void op_not() {
    int a = getA8(op);
    int b = getB9(op);

    cm.vSetStackVar(a, ()->{
      cm.vGetStackVar(b);
      cm.vBoolEval(true, false);
    });
  }

  void op_len(State s) {
    int a = getA8(op);
    int b = getB9(op);

    final Label end = new Label();
    final Label meta = new Label();
    LocalVar res = s.newVar("res");
    LocalVar obj = s.newVar("name");
    LocalVar func = s.newVar("func");
    cm.vGetStackVar(b);
    cm.vCopyRef();
    obj.store();

    cm.vIf(KahluaTable.class, ()-> {
      obj.load();
      cm.vCast(KahluaTable.class);
      cm.vInvokeInterface(KahluaTable.class, "len");
      mv.visitInsn(I2D);
      cm.vToObjectDouble(false);
      res.store();
      cm.vGoto(end);
    });

    obj.load();
    cm.vIf(String.class, ()-> {
      obj.load();
      cm.vCast(String.class);
      cm.vInvokeFunc(String.class, "length");
      mv.visitInsn(I2D);
      cm.vToObjectDouble(false);
      res.store();
      cm.vGoto(end);
    });

    cm.vBlock(meta, end, ()-> {
      cm.vGetMetaOp("__len", ()-> obj.load());
      cm.vCopyRef();
      func.store();

      cm.vIf(IFNULL, ()-> cm.vThrow("__len not defined for operand"));

      cm.vCall(new IBuildParam4() {
        public void param1() { func.load(); }
        public void param2() { obj.load(); }
        public void param3() { cm.vNull(); }
        public void param4() { cm.vNull(); }
      });
      res.store();
    });

    cm.vLabel(end, line);
    cm.vSetStackVar(a, ()-> res.load());
  }


  void op_concat(State s) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    cm.vInt(c);
    s.vCallframe.load();
    cm.vInvokeFunc(LuaScript.class, "auto_op_concat", I,I,I, LuaCallFrame.class);
  }


  //TODO: close all upvalues >= R(A:0 - 1) ??
  void op_jmp(State s) {
    final int a = getA8(op);
    final int i = getSBx(op);
    cm.vGoto(s.jumpToLabel(i));

    if (a > 0) {
      Tool.pl("flag_op_jmp_close_all_upvalues", a);
    }
  }


  /**
   * EQ  A B C if ((RK(B) == RK(C)) ~= A) then PC++
   */
  void op_eq(State s) {
    op_cmp(s, new ICompOp() {
      public void intComp() {
        cm.vIf(IFEQ, new IIF() {
          public void doThen() {
            cm.vBoolean(true);
          }
          public void doElse() {
            cm.vBoolean(false);
          }
        });
      }

      public void metaComp(int bo, int co) {
        cm.vThis();
        mv.visitVarInsn(ALOAD, bo);
        mv.visitVarInsn(ALOAD, co);
        cm.vInvokeFunc(LS, "try_comp_eq", O,O);
      }
    });
  }


  /**
   * LT  A B C if ((RK(B) <  RK(C)) ~= A) then PC++
   */
  void op_lt(State s) {
    op_cmp(s, new ICompOp() {
      public void intComp() {
        cm.vIf(IFLT, new IIF() {
          public void doThen() {
            cm.vBoolean(true);
          }
          public void doElse() {
            cm.vBoolean(false);
          }
        });
      }

      public void metaComp(int bo, int co) {
        cm.vThis();
        mv.visitVarInsn(ALOAD, bo);
        mv.visitVarInsn(ALOAD, co);
        cm.vInvokeFunc(LS, "try_comp_lt", O,O);
      }
    });
  }


  /**
   * LE  A B C if ((RK(B) <= RK(C)) ~= A) then PC++
   */
  void op_le(State s) {
    op_cmp(s, new ICompOp() {
      public void intComp() {
        cm.vIf(IFLE, new IIF() {
          public void doThen() {
            cm.vBoolean(true);
          }
          public void doElse() {
            cm.vBoolean(false);
          }
        });
      }

      public void metaComp(int bo, int co) {
        cm.vThis();
        mv.visitVarInsn(ALOAD, bo);
        mv.visitVarInsn(ALOAD, co);
        cm.vInvokeFunc(LS, "try_comp_le", O,O);
      }
    });
  }

  void op_cmp(State s, ICompOp cp) {
    final int a = getA8(op);
    final int b = getB9(op);
    final int c = getC9(op);

    final Label jump = s.jumpn1();
    final Label njump = new Label();
    final Label isnum = new Label();
    final Label isstr = new Label();
    final Label rcomp = new Label();

    final int bo = s.nextVarid();
    final int co = s.nextVarid();
    final int azero = s.nextVarid();

    cm.vBoolean(a == 0);
    mv.visitVarInsn(ISTORE, azero);

    cm.vGetRegOrConst(b);
    mv.visitVarInsn(ASTORE, bo);
    cm.vGetRegOrConst(c);
    mv.visitVarInsn(ASTORE, co);

    // ------- check if double

    mv.visitVarInsn(ALOAD, bo);
    cm.vIf(Double.class, new IIFwe() {
      public void doThen() {
        mv.visitVarInsn(ALOAD, co);
        cm.vIf(Double.class, new IIFwe() {
          public void doThen() {
            cm.vGoto(isnum);
          }
        });
      }
    });

    // ------- check if string

    mv.visitVarInsn(ALOAD, bo);
    cm.vIf(String.class, new IIFwe() {
      public void doThen() {
        mv.visitVarInsn(ALOAD, co);
        cm.vIf(String.class, new IIFwe() {
          public void doThen() {
            cm.vGoto(isstr);
          }
        });
      }
    });

    // ------- else

    cp.metaComp(bo, co);
    cm.vGoto(rcomp);

    // ------- string

    cm.vLabel(isstr, line);
    mv.visitVarInsn(ALOAD, bo);
    cm.vCast(String.class);
    mv.visitVarInsn(ALOAD, co);
    cm.vCast(String.class);
    cm.vInvokeFunc(String.class, "compareTo", S);

    cp.intComp();
    cm.vGoto(rcomp);

    // ------- number

    cm.vLabel(isnum, line);
    mv.visitVarInsn(ALOAD, bo);
    cm.vCast(Double.class);
    cm.vInvokeFunc(Double.class, "doubleValue");
    mv.visitVarInsn(ALOAD, co);
    cm.vCast(Double.class);
    cm.vInvokeFunc(Double.class, "doubleValue");
    mv.visitInsn(DCMPG);

    cp.intComp();
    cm.vGoto(rcomp);

    // ------- rcomp

    cm.vLabel(rcomp, line);
    mv.visitVarInsn(ILOAD, azero);
    cm.vIf(IF_ICMPEQ, new IIF() {
      public void doThen() {
        cm.vGoto(jump);
      }
      public void doElse() {
        cm.vGoto(njump);
      }
    });
    cm.vGoto(njump);

    // ------- no jump

    cm.vLabel(njump, line);
  }

  void op_test(State s) {
    final int a = getA8(op);
    final int c = getC9(op);
    final boolean eqt = (c == 0);

    Label jumpto = s.jumpn1();

    cm.vGetStackVar(a);
    cm.vBoolEval(false, true);
    cm.vBoolean(eqt);

    cm.vIf(IF_ICMPEQ, new IIFwe() {
      public void doThen() {
        cm.vGoto(jumpto);
      }
    });
  }

  void op_testset(State s) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);
    boolean eqt = (c == 0);

    Label jumpto = s.jumpn1();
    LocalVar value = s.newVar("value");

    cm.vGetStackVar(b);
    cm.vCopyRef();
    value.store();
    cm.vBoolEval(false, true);
    cm.vBoolean(eqt);

    cm.vIf(IF_ICMPEQ, new IIF() {
      public void doThen() {
        cm.vGoto(jumpto);
      }

      public void doElse() {
        cm.vSetStackVar(a, () -> value.load());
      }
    });
  }

  void op_forprep(State s) {
    int a = getA8(op);
    int b = getSBx(op);

    cm.vSetStackVar(a, () -> {
      cm.vGetStackVar(a);
      cm.vToPrimitiveDouble(true);
      cm.vGetStackVar(a + 2);
      cm.vToPrimitiveDouble(true);
      mv.visitInsn(DSUB);
      cm.vToObjectDouble(false);
    });

    cm.vGoto(s.jumpToLabel(b));
  }

  void op_forloop(State s) {
    int a = getA8(op);
    int b = getSBx(op);

    final Label jumpTo = s.jumpToLabel(b);
    final Label ret = new Label();
    LocalVar iter = s.newVar(OD, "iter");
    LocalVar step = s.newVar(OD, "step");
    LocalVar end  = s.newVar(OD, "end");

    cm.vGetStackVar(a);
    iter.store();

    cm.vGetStackVar(a + 1);
    end.store();

    cm.vGetStackVar(a + 2);
    step.store();

    // iter = item + step
    iter.load();
    cm.vToPrimitiveDouble(true);
    step.load();
    cm.vToPrimitiveDouble(true);
    mv.visitInsn(DADD);
    cm.vToObjectDouble(false);
    iter.store();

    cm.vSetStackVar(a, () -> iter.load());

    final IIF checkloop = new IIF() {
      public void doThen() {
        cm.vSetStackVar(a + 3, ()-> iter.load());
        cm.vGoto(jumpTo);
      }
      public void doElse() {
        cm.vClearStack(a);
        cm.vGoto(ret);
      }
    };

    iter.load();
    cm.vToPrimitiveDouble(true);
    end.load();
    cm.vToPrimitiveDouble(true);
    mv.visitInsn(DCMPG);

    step.load();
    cm.vToPrimitiveDouble(true);
    mv.visitInsn(D2I);
    cm.vIf(IFGT, new IIF() {
      public void doThen() {
        cm.vIf(IFLE, checkloop);
      }
      public void doElse() {
        cm.vIf(IFGE, checkloop);
      }
    });

    mv.visitLabel(ret);
  }


  void op_tforloop(State stat) {
    final int a = getA8(op);
    final int c = getC9(op);

    cm.vSetFrameTop(()-> cm.vInt(a + 6));
    cm.vFrameStackCopy(new IBuildParam3() {
      public void param1() {
        cm.vInt(a);
      }
      public void param2() {
        cm.vInt(a +3);
      }
      public void param3() {
        cm.vInt(3);
      }
    });

    cm.vThis();
    cm.vInt(2);
    cm.vInvokeFunc(LS, "call", I);
    cm.vPop(); // drop return value

    cm.vClearStack(a + 3 + c);
    stat.vCallframe.load();
    cm.vInvokeFunc(FR, "setPrototypeStacksize");

    cm.vGetStackVar(a + 3);
    cm.vIf(IFNONNULL, new IIF() {
      public void doThen() {
        cm.vSetStackVar(a+2, ()-> cm.vGetStackVar(a + 3));
      }
      public void doElse() {
        cm.vGoto(stat.jumpn1());
      }
    });
  }


  /**
   * SETLIST A B C   R(A)[(C-1)*FPF+i] := R(A+i), 1 <= i <= B
   * A: function
   * B: elementsCount
   * C:
   * @param state
   */
  void op_setlist(State state) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    final LocalVar count = state.newVar(I, "count");
    final LocalVar table = state.newVar(O, "table");
    final LocalVar offset = state.newVar(I, "offset");
    final LocalVar i = state.newVar(I, "i");

    final Label check = new Label();
    final Label forend = new Label();

    if (b != 0) {
      cm.vInt(b);
      count.store();
    } else {
      cm.vGetTop();
      cm.vInt(a);
      mv.visitInsn(ISUB);
      cm.vInt(1);
      mv.visitInsn(ISUB);
      count.store();
    }

    if (c == 0) {
      c = state.readNextOp();
    }
    cm.vInt((c - 1) * KahluaThread2.FIELDS_PER_FLUSH);
    offset.store();

    cm.vGetStackVar(a);
    cm.vCast(KahluaTable.class);
    table.store();

    // init for()
    cm.vInt(1);
    i.store();
    cm.vGoto(check);

    // check for()
    mv.visitLabel(check);
    i.load();
    count.load();
    cm.vIf(IF_ICMPLE, new IIF() {
      public void doThen() {
        table.load();
        // param1
        offset.load();
        i.load();
        mv.visitInsn(IADD);
        mv.visitInsn(I2D);
        cm.vToObjectDouble(false);
        // param2
        cm.vGetStackVar(()->{
          cm.vInt(a);
          i.load();
          mv.visitInsn(IADD);
        });
        cm.vInvokeInterface(KahluaTable.class, "rawset", O,O);
      }
      public void doElse() {
        cm.vGoto(forend);
      }
    });
    i.load();
    cm.vInt(1);
    mv.visitInsn(IADD);
    i.store();
    cm.vGoto(check);

    // end for()
    mv.visitLabel(forend);
  }


  void op_close() {
    int a = getA8(op);

    cm.vCloseLocalUpvlues(()-> cm.vInt(a));
  }


  void op_vararg() {
    int a = getA8(op);
    int b = getB9(op) - 1;

    cm.vPushVarargs(new IBuildParam2() {
      public void param1() {
        cm.vInt(a);
      }
      public void param2() {
        cm.vInt(b);
      }
    });
  }


  void op_call(State state) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    LocalVar func = state.newVar(O, "function");
    LocalVar nArguments2 = state.newVar(I, "nArguments2");
    LocalVar clazz = state.newVar(Class.class, "class");

    cm.vGetStackVar(a);
    func.store();
    cm.vSetRestoreTop(c != 0);

    if (b != 0) {
      cm.vSetFrameTop(()-> cm.vInt(a+b)); // is right
      cm.vInt(b - 1);
      nArguments2.store();
    } else {
      cm.vGetTop();
      cm.vInt(a+1);
      mv.visitInsn(ISUB);
      nArguments2.store();
    }

    if (di.has(DebugInf.CALL)) {
      func.load();

      cm.vIf(IFNONNULL, new IIF() {
        public void doThen() {
          func.load();
          cm.vInvokeFunc(Object.class, "getClass");
          clazz.store();
          cm.vPrint("CallFunction:", clazz, func, "(..)");
        }
        public void doElse() {
          cm.vPrint("CallFunction got null object");
        }
      });
    }

    cm.vThis();
    state.vCI.load();
    state.vCallframe.load();
    func.load();
    nArguments2.load();
    cm.vInt(a);
    cm.vInt(a+b);
    cm.vInvokeFunc(LuaScript.class, "call", CI,FR,O,I,I,I);

    cm.vAutoRestoreTop();
  }


  //TODO: correct implementation
  void op_tailcall(State state) {
    op_call(state);
  }


  /**
   * RETURN  A B return R(A), ... ,R(A+B-2)
   */
  void op_return(State state) {
    int a = getA8(op);
    int b = getB9(op);

    LocalVar vb = state.newVar(I, "b");
    Label setTop = new Label();
    Label end = new Label();

    // This will fix the reference, making it a constant
    cm.vCloseCoroutineUpvalues(()-> cm.vGetBase());

    if (b == 0) {
      cm.vGetTop();
      cm.vInt(a);
      mv.visitInsn(ISUB);
      vb.store();
    } else {
      cm.vInt(b - 1);
      vb.store();
    }

    cm.vStackCopy(new IBuildParam3() {
      public void param1() {
        cm.vGetBase();
        cm.vInt(a);
        mv.visitInsn(IADD);
      }
      public void param2() {
        cm.vReturnBase();
      }
      public void param3() {
        vb.load();
      }
    });

    // This will make the variable after top null
    mv.visitLabel(setTop);
    cm.vSetCoroutineTop(()-> {
      cm.vReturnBase();
      vb.load();
      mv.visitInsn(IADD);
    });

    mv.visitLabel(end);
    cm.vGoto(state.returnLabel);
  }


  void op_closure(State state) {
    int a = getA8(op);
    int b = getBx(op);

    Prototype p = state.ci.prototype.prototypes[b];
    String luaName = CANNOT_BIND_LUA_NAME; //state.ci.prototype.constants[b] +"";
    ClosureInf newci = pushClosure(p, closureFuncName(p), a, luaName);
    LocalVar ci = state.newVar(ClosureInf.class, "ci");

    cm.vField("plist");
    cm.vInt(newci.arrIndex);
    mv.visitInsn(AALOAD);
    cm.vCopyRef();
    ci.store();

    cm.vThis();
    cm.vInvokeFunc(ClosureInf.class, "installMethod", LuaScript.class);
    cm.vSetStackVar(a, ()-> ci.load());

    for (int i=0; i<p.numUpvalues; ++i) {
      state.readNextOp();
      final int nb = getB9(op);

      // newci.upvalues[i] =
      ci.load();
      cm.vField(ClosureInf.class, "upvalues");
      cm.vInt(i);

      switch (opcode) {
      case OP_MOVE:
        cm.vFindUpvalue(()-> cm.vInt(nb));
        break;

      case OP_GETUPVAL:
        cm.vGetUpvalueFromClosure(()-> cm.vInt(nb));
        break;

      default:
        throw new RuntimeException("bad operate code in op_closure");
      }
      mv.visitInsn(AASTORE);
    }

    if (di.has(DebugInf.UPVALUE)) {
      cm.vPrintCiUpvalue(ci, 0);
    }
  }
}
