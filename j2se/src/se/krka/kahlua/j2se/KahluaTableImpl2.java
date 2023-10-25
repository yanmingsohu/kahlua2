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

package se.krka.kahlua.j2se;

import se.krka.kahlua.vm2.Tool;

import java.util.Map;


public class KahluaTableImpl2 extends KahluaTableImpl {

  private Object lastGetKey;
  private Object lastGetValue;

  public static int all = 0;
  public static int lost = 0;


  public KahluaTableImpl2(Map<Object, Object> delegate) {
    super(delegate);
  }


  // No performance improvement
  public void __rawset(Object key, Object value) {
    if (key == lastGetKey) {
    lastGetValue = value;
    }
    super.rawset(key, value);
  }


  // No performance improvement
  public Object __rawget(Object key) {
    //++all;
    if (lastGetKey != key) {
      lastGetKey = key;
      lastGetValue = super.rawget(key);
      //++lost;
    }
    return lastGetValue;
  }


  @Override
  public String toString() {
    return "table2 0x" + Tool.hash(this);
  }


  public static Double hisRate() {
    return ((all-lost)/(double)all);
  }
}
