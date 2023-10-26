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
import se.krka.kahlua.vm2.Tool;

import java.lang.ref.Cleaner;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;


/**
 * There is no memory management policy by default.
 * Memory usage only increases and will not decrease.
 */
public class J2SEPlatform2 extends J2SEPlatform {

  private static J2SEPlatform2 INSTANCE = new J2SEPlatform2();
  private TableRecycle recy = new TableRecycle();
  private boolean concurrent = true;


  public static J2SEPlatform2 getInstance() {
    return INSTANCE;
  }


  public void useConcurrent(boolean use) {
    this.concurrent = use;
  }


  @Override
  public KahluaTable newTable() {
    return recy.newUserTable(concurrent);
  }



  @Override
  public KahluaTable newEnvironment() {
    KahluaTable env = newTable();
    setupEnvironment(env);
    return env;
  }


  public Map createMap() {
    if (concurrent) {
      return new ConcurrentHashMap<>();
    } else {
      return new HashMap<>();
    }
  }


  /**
   * A memory management policy must be set
   */
  public void setMemoryManager(IMemoryReleaseStrategy m) {
    recy.updateMemoryStrategy(m);
  }


  private class TableRecycle implements ITableRecycle, ThreadFactory {

    private Queue<RecyclePackage> mcache = new ConcurrentLinkedQueue<>();
    private Queue<RecyclePackage> acache = new ConcurrentLinkedQueue<>();
    private ArrayBlockingQueue<RecyclePackage> work = new ArrayBlockingQueue<>(20);
    private Cleaner cleaner = Cleaner.create(this);
    private Thread memManager;
    private boolean stopMemm;


    TableRecycle() {
      newThread("Table Recycle", ()->{
        try {
          for (;;) {
            process(work.take());
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      });
    }


    synchronized void updateMemoryStrategy(IMemoryReleaseStrategy memr) {
      if (memr == null) {
        throw new NullPointerException();
      }

      if (memManager != null) {
        stopMemm = true;
        Tool.join(memManager);
      }

      stopMemm = false;
      memManager = newThread("Memory release", ()-> {
        while (!stopMemm) {
          Tool.sleep(memr.interval());
          memr.release(mcache, RecyclePackage.Type.Map);
          memr.release(acache, RecyclePackage.Type.Array);
        }
      });
    }


    KahluaTable newUserTable(boolean concurrent) {
      KahluaTableImpl2 kt2 = new KahluaTableImpl2(this);
      kt2.registerTo(cleaner);
      return kt2;
    }


    private void process(RecyclePackage t) {
      ((KahluaTable)t.data).wipe();

      if (t.key == RecyclePackage.Type.Map) {
        mcache.add(t);
      }
      else if (t.key == RecyclePackage.Type.Array) {
        acache.add(t);
      }
      else {
        throw new IllegalArgumentException("Unknow type "+ t.key);
      }
    }

    @Override
    public void recycle(RecyclePackage d) {
      work.offer(d);
    }

    @Override
    public KahluaTable createArrayTable(ITableSwitcher s) {
      RecyclePackage rp = acache.poll();
      if (rp == null) {
        return new ArrayTable(s);
      } else {
        ArrayTable at = (ArrayTable) rp.data;
        at.bind(s);
        return at;
      }
    }

    @Override
    public KahluaTable createMapTable() {
      RecyclePackage rp = mcache.poll();
      if (rp == null) {
        return new KahluaTableImpl(createMap());
      } else {
        return (KahluaTable) rp.data;
      }
    }


    @Override
    public Thread newThread(Runnable r) {
      return new Thread(r);
    }


    public Thread newThread(String name, Runnable r) {
      Thread t = newThread(r);
      t.setName(name);
      t.setDaemon(true);
      t.start();
      return t;
    }
  }
}
