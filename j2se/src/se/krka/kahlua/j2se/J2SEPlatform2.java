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

import se.krka.kahlua.vm.KahluaTable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class J2SEPlatform2 extends J2SEPlatform {

  private static J2SEPlatform2 INSTANCE = new J2SEPlatform2();


  public static J2SEPlatform2 getInstance() {
    return INSTANCE;
  }


  @Override
  public KahluaTable newTable() {
    return newTable(true);
  }


  public KahluaTable newTable(boolean concurrent) {
    return new KahluaTableImpl2(new NewMapTable(concurrent));
  }


  @Override
  public KahluaTable newEnvironment() {
    return newEnvironment(true);
  }


  public KahluaTable newEnvironment(boolean concurrent) {
    KahluaTable env = newTable(concurrent);
    setupEnvironment(env);
    return env;
  }


  public static class NewMapTable {

    private boolean concurrent;

    public NewMapTable(boolean concurrent) {
      this.concurrent = concurrent;
    }


    public Map create() {
      if (concurrent) {
        return new ConcurrentHashMap<>();
      } else {
        return new HashMap<>();
      }
    }


    /**
     * Must return Table implements store object with Map
     */
    KahluaTable createMapTable() {
      return new KahluaTableImpl(create());
    }
  }
}
