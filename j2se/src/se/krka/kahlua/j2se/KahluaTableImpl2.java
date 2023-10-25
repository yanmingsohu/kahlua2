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

import se.krka.kahlua.vm.KahluaTableIterator;
import se.krka.kahlua.vm.LuaCallFrame;
import se.krka.kahlua.vm2.Tool;

import java.util.Iterator;
import java.util.Map;


public class KahluaTableImpl2 extends KahluaTableImpl {

  private Object[] list;
  private int listLength;
  private boolean onlyArray;


  public KahluaTableImpl2(Map<Object, Object> delegate) {
    super(delegate);
    this.list = new Object[55];
    this.listLength = 0;
    this.onlyArray = true;
  }


  private void _seti(int key, Object value) {
    if (key >= list.length) {
      Object[] newList = new Object[key * 2];
      System.arraycopy(list, 0, newList, 0, list.length);
      list = newList;
    }
    if (key >= listLength) {
      listLength = key +1;
    }
    list[key] = value;
  }


  private Object _geti(int key) {
    if (key < list.length) {
      return list[key];
    }
    return null;
  }


  public Object rawget(int key) {
    Object r = _geti(key);
    if (r != null) {
      return r;
    }
    if (!onlyArray) {
      return super.rawget(Double.valueOf(key));
    }
    return null;
  }


  public void rawset(int key, Object value) {
    _seti(key, value);
    if (!onlyArray) {
      super.rawset(Double.valueOf(key), value);
    }
  }


  public void rawset(Object key, Object value) {
    onlyArray = false;
    if (key instanceof Double) {
      Double n = (Double)key;
      double d = (double)n;
      int i = (int)d;
      if (i == d && i >= 0) {
        _seti(i, value);
      }
    }
    super.rawset(key, value);
  }


  public Object rawget(Object key) {
    onlyArray = false;
    if (key instanceof Double) {
      Double n = (Double)key;
      double d = (double)n;
      int i = (int)d;
      if (i == d && i >= 0) {
        Object v = _geti(i);
        if (v != null) return v;
      }
    }
    return super.rawget(key);
  }


  @Override
  public String toString() {
    return "table2 0x" + Tool.hash(this);
  }


  public int len() {
    return super.len();
  }


  public boolean isEmpty() {
    return super.isEmpty() && (listLength == 0);
  }


  public void wipe() {
    super.wipe();
    listLength = 0;
  }


  public KahluaTableIterator iterator() {
    final KahluaTableIterator mapIt = super.iterator();

    return new KahluaTableIterator() {
      private Object curKey;
      private Object curValue;
      boolean mapOver = false;
      int listi = 0;

      @Override
      public int call(LuaCallFrame callFrame, int nArguments) {
        if (advance()) {
          return callFrame.push(getKey(), getValue());
        }
        return 0;
      }

      @Override
      public boolean advance() {
        if (!mapOver) {
          boolean ma = mapIt.advance();
          curKey = mapIt.getKey();
          curValue = mapIt.getValue();
          mapOver = !ma;
          return ma;
        }

        if (listi < listLength) {
          curKey = Double.valueOf(listi);
          curValue = list[listi];
          return true;
        } else {
          curKey = null;
          curValue = null;
        }
        return false;
      }

      public Object getKey() {
        return curKey;

      }

      public Object getValue() {
        return curValue;
      }
    };

  }
}
