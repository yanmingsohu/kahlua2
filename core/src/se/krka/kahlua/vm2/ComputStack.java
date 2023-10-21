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


/**
 * A java function returns an integer value indicating number of results returned so for java function calls this is used.
 *
 * For Lua functions, the results are saved by the called function’s OP_RETURN instruction.
 *
 * stack:
 * ---
 * | <- returnBase
 * | <- localBase
 * | <- Arg 1
 * |    ...
 * | <- Arg x
 * | <- top
 * ---
 */
public class ComputStack {

  public final int top;
  public final int nArguments;
  public final int localBase;
  public final int returnBase;


  public ComputStack(Coroutine cr, int nArguments) {
    this.top = cr.getTop();
    this.nArguments = nArguments;
    this.localBase = top - nArguments;
    this.returnBase = localBase - 1;
  }


  public ComputStack(int t, int na, int lb, int rb) {
    this.top = t;
    this.nArguments = na;
    this.localBase = lb;
    this.returnBase = rb;
  }


  /**
   *
   * @param copy
   * @param funcBase from OP_CALL(a)
   */
  public ComputStack(LuaCallFrame copy, int funcBase, int nArguments) {
    this.localBase = copy.localBase + funcBase + 1;
    this.returnBase = localBase - 1;
    this.nArguments = nArguments;
    this.top = Integer.MIN_VALUE;
  }


  public int returnValues(Coroutine cr) {
    return cr.getTop() - returnBase;
  }


  public String toString() {
    String t = top < 0 ? "Unset" : ""+ top;
    return "ComputStack{ Top:"+ t
      +" localBase:"+ localBase
      +" returnBase:"+ returnBase
      +" nArg:"+ nArguments
      +" @"+ System.identityHashCode(this)
      +" }";
  }


  public LuaCallFrame pushFrame(Coroutine c, Prototype pt) {
    return pushFrame(c, pt, null);
  }


  public LuaCallFrame pushFrame(Coroutine c, JavaFunction jf) {
    return pushFrame(c, null, jf);
  }


  private LuaCallFrame pushFrame(Coroutine c, Prototype pt, JavaFunction jf) {
    LuaClosure lc = null;
    if (jf == null) {
      lc = new LuaClosure(pt, c.environment);
    }

    LuaCallFrame cf = c.pushNewCallFrame(
      lc, jf, localBase, returnBase, nArguments, jf == null, false);

    //Tool.pl("(0==", cf.localBase, cf.returnBase, cf.nArguments, cf.hashCode(), ')');
    //cf.init();
    return cf;
  }
}
