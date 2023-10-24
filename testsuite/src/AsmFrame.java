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

import se.krka.kahlua.vm2.Tool;
import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;


public class AsmFrame {

  public void __asm() {
    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "__java", "()V", null, null);
    methodVisitor.visitCode();
    Label label0 = new Label();
    methodVisitor.visitLabel(label0);
    methodVisitor.visitLineNumber(34, label0);
    methodVisitor.visitInsn(ICONST_0);
    methodVisitor.visitVarInsn(ISTORE, 1);
    Label label1 = new Label();
    methodVisitor.visitLabel(label1);
    methodVisitor.visitLineNumber(35, label1);
    methodVisitor.visitIntInsn(BIPUSH, 99);
    methodVisitor.visitVarInsn(ISTORE, 2);
    Label label2 = new Label();
    methodVisitor.visitLabel(label2);
    methodVisitor.visitLineNumber(37, label2);
    methodVisitor.visitVarInsn(ILOAD, 1);
    methodVisitor.visitVarInsn(ILOAD, 2);
    Label label3 = new Label();
    methodVisitor.visitJumpInsn(IF_ICMPGE, label3);

    Label label4 = new Label();
    methodVisitor.visitLabel(label4);
    methodVisitor.visitLineNumber(38, label4);
    methodVisitor.visitLdcInsn("a");
    methodVisitor.visitMethodInsn(INVOKESTATIC, "se/krka/kahlua/vm2/Tool", "pl", "(Ljava/lang/Object;)V", false);
    Label label5 = new Label();
    methodVisitor.visitJumpInsn(GOTO, label5);

    methodVisitor.visitLabel(label3);
    methodVisitor.visitLineNumber(40, label3);
    methodVisitor.visitFrame(F_NEW, 2, new Object[]{Opcodes.INTEGER, Opcodes.INTEGER}, 0, new Object[0]);
    methodVisitor.visitLdcInsn("b");
    methodVisitor.visitMethodInsn(INVOKESTATIC, "se/krka/kahlua/vm2/Tool", "pl", "(Ljava/lang/Object;)V", false);

    methodVisitor.visitLabel(label5);
    methodVisitor.visitLineNumber(42, label5);
    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    methodVisitor.visitInsn(RETURN);
    Label label6 = new Label();
    methodVisitor.visitLabel(label6);
    methodVisitor.visitLocalVariable("this", "LAsmFrame;", null, label0, label6, 0);
    methodVisitor.visitLocalVariable("a", "I", null, label1, label6, 1);
    methodVisitor.visitLocalVariable("b", "I", null, label2, label6, 2);
    methodVisitor.visitMaxs(2, 3);
    methodVisitor.visitEnd();
  }


  public void __java() {
    int a= 0;
    int b= 99;

    if (a < b) {
      Tool.pl("a");
    } else {
      Tool.pl("b");
    }
  }


  public static void main(String[] arg) {
    AsmFrame af = new AsmFrame();
    af.__asm();
  }
}
