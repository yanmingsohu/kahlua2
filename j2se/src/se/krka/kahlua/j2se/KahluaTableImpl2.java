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
import se.krka.kahlua.vm2.Tool;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;


public class KahluaTableImpl2 implements KahluaTable, ITableSwitcher {

  private IMapCreator mapCreator;
  private ITableRecycle recy;
  private KahluaTable impl;
  private Cleanning c;


  public KahluaTableImpl2(IMapCreator creator, ITableRecycle recy) {
    this.mapCreator = creator;
    this.recy = recy;
    this.impl = recy.createArrayTable(this);
    this.c = new Cleanning(recy, (ICanbeRecycled)this.impl);
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
    return "Table2 0x" + Tool.hash(this);
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


  @Override
  public KahluaTable switchToMap(KahluaTable meta, Object[] values) {
    KahluaTable v1 = recy.createMapTable(mapCreator);
    v1.setMetatable(meta);

    for (int i=0; i<values.length; ++i) {
      Object v = values[i];
      if (v != null) {
        v1.rawset(Double.valueOf(i), v);
      }
    }

    c.run();
    c.update((ICanbeRecycled)v1);

    impl = v1;
    mapCreator = null;

    return impl;
  }


  public void registerTo(Cleaner c) {
    c.register(this, this.c);
  }


  private static class Cleanning implements Runnable {

    RecyclePackage data;
    ITableRecycle recy;

    // Do not reference target
    public Cleanning(ITableRecycle recy, ICanbeRecycled target) {
      this.recy = recy;
      update(target);
    }

    private void update(ICanbeRecycled target) {
      data = target.getRecyclePackage();
    }

    @Override
    public void run() {
      recy.recycle(data);
    }
  }
}
