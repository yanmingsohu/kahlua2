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

import java.util.LinkedList;
import java.util.List;


public abstract class StateBase implements IConst {

  private final MethodVisitor mv;
  private int varId = -1;
  private List<LocalVar> outputVarDebugInf;


  protected StateBase(MethodVisitor mv) {
    this.mv = mv;
    this.outputVarDebugInf = new LinkedList<>();
  }


  public void resetVarIndex() {
    varId = 1;
  }


  public LocalVar newVar(Class c, String name, Label s, Label e) {
    LocalVar v = new LocalVar(mv, c, nextVarid(), name, s, e);
    if (name != null && s != null && e != null) {
      outputVarDebugInf.add(v);
    }
    return v;
  }


  public void vAllVariables() {
    for (LocalVar lv : outputVarDebugInf) {
      lv.output();
    }
  }


  public int nextVarid() {
    return nextVarid(vUser);
  }


  public int nextVarid(int offset) {
    if (offset > 0) {
      return offset + (varId++);
    }
    return (varId++);
  }
}
