/*
Copyright (c) 2008 Kristofer Karlsson <kristofer.karlsson@gmail.com>

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
package se.krka.kahlua.vm;

public class LuaCallFrame {
	private final Platform platform;

	public final Coroutine coroutine;

	public LuaCallFrame(Coroutine coroutine) {
		this.coroutine = coroutine;
		platform = coroutine.getPlatform();
	}
	
	public LuaClosure closure;
	public JavaFunction javaFunction;

	public int pc;

	public int localBase;
	public int returnBase;
	public int nArguments;

	boolean fromLua;
	public boolean canYield;
	
	public boolean restoreTop;
	
	public final void set(int index, Object o) {
		/*
		if (index > getTop()) {
			throw new KahluaException("Tried to access index outside of stack, top: " + getTop() + ", index: " + index);
		}
		*/
		coroutine.objectStack[localBase + index] = o;
	}

	public final Object get(int index) {
		/*
		if (index > getTop()) {
			throw new KahluaException("Tried to access index outside of stack, top: " + getTop() + ", index: " + index);
		}
		*/
		return coroutine.objectStack[localBase + index];
	}

	public int push(Object x) {
		int top = getTop();
		setTop(top + 1);
		set(top, x);
		return 1; // returns how much we pushed onto the stack for return value purposes
	}

	public int push(Object x, Object y) {
		int top = getTop();
		setTop(top + 2);
		set(top, x);
		set(top + 1, y);
		return 2; // returns how much we pushed onto the stack for return value purposes
	}
	
	public int pushNil() {
		return push(null);
	}
	
	public final void stackCopy(int startIndex, int destIndex, int len) {
		coroutine.stackCopy(localBase + startIndex, localBase + destIndex, len);
	}
	
	public void stackClear(int startIndex, int endIndex) {
		for (; startIndex <= endIndex; startIndex++) {
			coroutine.objectStack[localBase + startIndex] = null;
		}
	}

	/**
	 * This ensures that top is at least as high as index, and that everything from index and up is empty.
	 * @param index
	 */
	public void clearFromIndex(int index) {
		if (getTop() < index) {
			setTop(index);
		}
		stackClear(index, getTop() - 1);
	}
	
	public final void setTop(int index) {
		coroutine.setTop(localBase + index);
	}

	public void closeUpvalues(int a) {
		coroutine.closeUpvalues(localBase + a);
	}

	public UpValue findUpvalue(int b) {
		return coroutine.findUpvalue(localBase + b);
	}

	public int getTop() {
		return coroutine.getTop() - localBase;
	}

	public void init() {
		if (isLua()) {
			pc = 0;
			if (closure.prototype.isVararg) {
				localBase += nArguments;
				
				setTop(closure.prototype.maxStacksize);
				int toCopy = Math.min(nArguments, closure.prototype.numParams);
				stackCopy(-nArguments, 0, toCopy);
			} else {
				setTop(closure.prototype.maxStacksize);
				stackClear(closure.prototype.numParams, nArguments);
			}
		}
	}

	public void setPrototypeStacksize() {
		if (isLua()) {
			setTop(closure.prototype.maxStacksize);
		}
	}
	
	public void pushVarargs(int index, int n) {
		int nParams = closure.prototype.numParams;
		int nVarargs = nArguments - nParams;
		if (nVarargs < 0) nVarargs = 0;
		if (n == -1) {
			n = nVarargs;
			setTop(index + n);
		}
		if (nVarargs > n) nVarargs = n;
		
		stackCopy(-nArguments + nParams, index, nVarargs);
		
		int numNils = n - nVarargs;
		if (numNils > 0) {
			stackClear(index + nVarargs, index + n - 1);
		}
	}

	public KahluaTable getEnvironment() {
		if (isLua()) {
			return closure.env;
		}
		return coroutine.environment;
	}

	public boolean isJava() {
		return !isLua();
	}
	
	public boolean isLua() {
		return closure != null;
	}

	public String toString() {
		if (closure != null) {
			return "Callframe at: " + closure.toString();
		}
		if (javaFunction != null) {
			return "Callframe at: " + javaFunction.toString();
		}
		return super.toString();
	}

	public Platform getPlatform() {
        return platform;
    }

	void setup(LuaClosure closure, JavaFunction javaFunction, int localBase, int returnBase, int nArguments, boolean fromLua, boolean insideCoroutine) {
		this.localBase = localBase;
		this.returnBase = returnBase;
		this.nArguments = nArguments;
		this.fromLua = fromLua;
		this.canYield = insideCoroutine;
		this.closure = closure;
		this.javaFunction = javaFunction;
	}

	public KahluaThread getThread() {
		return coroutine.getThread();
	}
}
