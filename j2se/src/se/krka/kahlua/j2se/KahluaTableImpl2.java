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
import se.krka.kahlua.vm.KahluaTableIterator;
import se.krka.kahlua.vm.LuaCallFrame;
import se.krka.kahlua.vm2.Tool;


public class KahluaTableImpl2 implements KahluaTable {

  public static int totalTable;
  public static int releaseArray;


  private J2SEPlatform2.NewMapTable mapCreator;
  private KahluaTable impl;


  public KahluaTableImpl2(J2SEPlatform2.NewMapTable creator) {
    this.mapCreator = creator;
    this.impl = new ArrayTable();
    //++totalTable;
  }


  public Object rawget(int key) {
    return impl.rawget(key);
  }


  public void rawset(int key, Object value) {
    impl.rawset(key, value);
  }


  public void rawset(Object key, Object value) {
    impl.rawset(key, value);
  }


  public Object rawget(Object key) {
    return impl.rawget(key);
  }


  @Override
  public String toString() {
    return "table2 0x" + Tool.hash(this);
  }


  public int len() {
    return impl.len();
  }


  public boolean isEmpty() {
    return impl.isEmpty();
  }


  public void wipe() {
    impl.wipe();
  }


  @Override
  public void setMetatable(KahluaTable metatable) {
    impl.setMetatable(metatable);
  }


  @Override
  public KahluaTable getMetatable() {
    return impl.getMetatable();
  }


  public KahluaTableIterator iterator() {
    return impl.iterator();
  }


  private KahluaTable switchToMap(ArrayTable at) {
    KahluaTable v1 = mapCreator.createMapTable();
    v1.setMetatable(at.getMetatable());
    for (int i=0; i<at.list.length; ++i) {
      Object v = at.list[i];
      if (v != null) {
        v1.rawset(Double.valueOf(i), v);
      }
    }
    impl = v1;
    mapCreator = null;
    //++releaseArray;
    return impl;
  }


  private class ArrayTable implements KahluaTable {
    private Object[] list;
    private KahluaTable meta;
    private int maxIndex = 0;
    private int minIndex = Integer.MAX_VALUE;
    private int qlength;

    private ArrayTable() {
      this.list = new Object[55];
      this.qlength = 0;
    }


    @Override
    public void setMetatable(KahluaTable metatable) {
      this.meta = metatable;
    }


    @Override
    public KahluaTable getMetatable() {
      return meta;
    }


    @Override
    public void rawset(Object key, Object value) {
      int index = tryUseArray(key);
      if (index >= 0) {
        rawset(index, value);
        return;
      }
      switchToMap(this).rawset(key, value);
    }


    @Override
    public Object rawget(Object key) {
      if (key == null) return null;
      int index = tryUseArray(key);
      if (index >= 0) {
        return rawget(index);
      }
      return null;
    }


    private int tryUseArray(final Object key) {
      if (key instanceof Double) {
        final Double dk = (Double)key;
        final int i = dk.intValue();
        if (dk == (double)i) {
          return i;
        }
      }
      return -1;
    }


    @Override
    public void rawset(int key, Object value) {
      if (key >= list.length) {
        Object[] newList = new Object[key * 2];
        System.arraycopy(list, 0, newList, 0, list.length);
        list = newList;
      }

      final Object old = list[key];

      if (value == null) {
        if (old != null) --qlength;
        list[key] = null;
      } else { // value != null
        if (old == null) ++qlength;
        list[key] = value;
      }

      if (key <= minIndex) {
        minIndex = key;
      }
      if (key >= maxIndex) {
        maxIndex = key +1;
      }
    }


    @Override
    public Object rawget(int key) {
      if (key < list.length) {
        return list[key];
      }
      return null;
    }


    @Override
    public int len() {
      return qlength;
    }


    @Override
    public KahluaTableIterator iterator() {
      return new ArrayIterator(this);
    }


    @Override
    public boolean isEmpty() {
      return qlength == 0;
    }


    @Override
    public void wipe() {
      for (int i=minIndex; i<maxIndex; ++i) {
        list[i] = null;
      }
      minIndex = Integer.MAX_VALUE;
      maxIndex = 0;
      qlength = 0;
    }
  }


  private class ArrayIterator implements KahluaTableIterator {
    private Double curKey;
    private Object curValue;
    private ArrayTable at;
    int i = 0;


    private ArrayIterator(ArrayTable at) {
      this.at = at;
    }


    @Override
    public int call(LuaCallFrame callFrame, int nArguments) {
      if (advance()) {
        return callFrame.push(curKey, curValue);
      }
      return 0;
    }


    @Override
    public boolean advance() {
      while (at.list[i] == null) {
        if (++i >= at.list.length) {
          curKey = null;
          curValue = null;
          return false;
        }
      }

      curKey = (double) i;
      curValue = at.list[i];
      return true;
    }


    @Override
    public Object getKey() {
      return curKey;
    }


    @Override
    public Object getValue() {
      return curValue;
    }
  }
}
