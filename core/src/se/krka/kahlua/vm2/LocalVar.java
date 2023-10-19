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

import java.util.HashMap;
import java.util.Map;

import static jdk.internal.org.objectweb.asm.Opcodes.*;


public class LocalVar {

  private static final Map<Class, Code> types = new HashMap();
  private static final Code ObjectCode = new Code(ALOAD, ASTORE);

  static {
    types.put(Double.class, new Code(DLOAD, DSTORE));
    types.put(double.class, new Code(DLOAD, DSTORE));
    types.put(Float.class, new Code(FLOAD, FSTORE));
    types.put(float.class, new Code(FLOAD, FSTORE));
    types.put(Integer.class, new Code(ILOAD, ISTORE));
    types.put(int.class, new Code(ILOAD, ISTORE));
    types.put(Long.class, new Code(LLOAD, LSTORE));
    types.put(long.class, new Code(LLOAD, LSTORE));
    types.put(Object.class, ObjectCode);
    types.put(Boolean.class, new Code(ILOAD, ISTORE));
    types.put(boolean.class, new Code(ILOAD, ISTORE));
  }

  private final MethodVisitor mv;
  private final int index;
  private final Code code;
  private final Class type;
  private boolean readonly;
  String name;
  Label s, e;


  LocalVar(MethodVisitor mv, Class whatType, int index, String name, Label s, Label e) {
    this.code = findCode(whatType);
    this.type = whatType;
    this.index = index;
    this.mv = mv;
    this.name = name;
    this.s = s;
    this.e = e;
  }


  private static Code findCode(Class c) {
    Code code = types.get(c);
    if (code == null) {
      //throw new RuntimeException("No opcode found for this type:"+ c);
      code = ObjectCode;
    }
    return code;
  }


  /**
   * load(from index) var to stack top
   */
  public void load() {
    mv.visitVarInsn(code.loadcode, index);
  }


  /**
   * store stack top value to var(on index)
   */
  public void store() {
    if (readonly) throw new RuntimeException("Is read only");
    mv.visitVarInsn(code.savecode, index);
  }


  void output() {
    StringBuilder buf = new StringBuilder();
    Tool.typeName(buf, type);
    mv.visitLocalVariable(name, buf.toString(), null, s, e, index);
  }


  void lock() {
    this.readonly = true;
  }


  private static class Code {

    private final int loadcode;
    private final int savecode;


    private Code(int loadcode, int savecode) {
      this.loadcode = loadcode;
      this.savecode = savecode;
    }
  }
}
