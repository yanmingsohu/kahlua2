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

import org.objectweb.asm.MethodVisitor;
import se.krka.kahlua.vm.LuaClosure;
import se.krka.kahlua.vm.UpValue;

import static org.objectweb.asm.Opcodes.*;


@Deprecated
public class VUpvalueOp implements IConst {

  private final ClassMaker cm;
  private final MethodVisitor mv;
  private final LocalVar tmpVarIndex;


  /**
   * Used to operate Upvalue objects
   * @param cm
   * @param mv
   * @param upindex index in vClosure array
   */
  VUpvalueOp(ClassMaker cm, MethodVisitor mv, int upindex, LuaBuilder.State s) {
    this.cm = cm;
    this.mv = mv;
    this.tmpVarIndex = s.newVar("tmp");

    s.vClosure.load();

    cm.vField(LuaClosure.class, "upvalues");
    mv.visitLdcInsn(upindex);
    mv.visitInsn(AALOAD);
    tmpVarIndex.store();
  }


  public void getValue() {
    tmpVarIndex.load();
    cm.vInvokeFunc(UpValue.class, "getValue");
  }


  public void setValue(IBuildParam p) {
    tmpVarIndex.load();
    p.param1();
    cm.vInvokeFunc(UpValue.class, "setValue", O);
  }
}
