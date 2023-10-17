/*
Copyright (c) Yanming <yanmingsohu@gmail.com>

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

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;


/**
 * When a function uses variables, it must store these variables
 * on the stack and restore these variables after use.
 * @deprecated Variable type cannot be determined
 */
public class TemporaryVar {

  private final MethodVisitor mv;
  private final int[] varIndex;


  public TemporaryVar(MethodVisitor _mv, int ...varIndex) {
    this.mv = _mv;
    this.varIndex = varIndex;
  }


  public void save() {
    for (int i=0; i<varIndex.length; ++i) {
      mv.visitVarInsn(ALOAD, varIndex[i]);
    }
  }


  public void revert() {
    for (int i=0; i<varIndex.length; ++i) {
      mv.visitVarInsn(ASTORE, varIndex[i]);
    }
  }
}
