/*
 Copyright (c) 2009 Kristofer Karlsson <kristofer.karlsson@gmail.com>, Per Malmén <per.malmen@gmail.com>

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

package se.krka.kahlua.integration;

import se.krka.kahlua.converter.KahluaConverterManager;
import se.krka.kahlua.vm.KahluaThread;

public class LuaCaller {
	
	private final KahluaConverterManager converterManager;

	public LuaCaller(KahluaConverterManager converterManager) {
		this.converterManager = converterManager;
	}

	public Object[] pcall(KahluaThread thread, Object functionObject, Object... args) {
		if (args != null) {
			for (int i = args.length - 1; i >= 0; i--) {
				args[i] = converterManager.fromJavaToLua(args[i]);
			}
		}
		Object[] results = thread.pcall(functionObject, args);
		return results;
	}

	public LuaReturn protectedCall(KahluaThread thread, Object functionObject, Object... args) {
		return LuaReturn.createReturn(pcall(thread, functionObject, args));
	}


	// Copy from PZ source

	public void pcallvoid(KahluaThread var1, Object var2, Object var3) {
		var1.pcallvoid(var2, var3);// 38
	}// 40

	public void pcallvoid(KahluaThread var1, Object var2, Object var3, Object var4) {
		var1.pcallvoid(var2, var3, var4);// 43
	}// 44

	public void pcallvoid(KahluaThread var1, Object var2, Object var3, Object var4, Object var5) {
		var1.pcallvoid(var2, var3, var4, var5);// 48
	}// 50

	public Boolean pcallBoolean(KahluaThread var1, Object var2, Object var3, Object var4) {
		return var1.pcallBoolean(var2, var3, var4);// 53
	}

	public Boolean pcallBoolean(KahluaThread var1, Object var2, Object var3, Object var4, Object var5) {
		return var1.pcallBoolean(var2, var3, var4, var5);// 57
	}

	public void pcallvoid(KahluaThread var1, Object var2, Object[] var3) {
		if (var3 != null) {// 61
			for(int var4 = var3.length - 1; var4 >= 0; --var4) {// 62
				var3[var4] = this.converterManager.fromJavaToLua(var3[var4]);// 63
			}
		}

		var1.pcallvoid(var2, var3);// 66
	}// 68

//	public Object[] pcall(KahluaThread var1, Object var2, Object... var3) {
//		if (var3 != null) {// 71
//			for(int var4 = var3.length - 1; var4 >= 0; --var4) {// 72
//				var3[var4] = this.converterManager.fromJavaToLua(var3[var4]);// 73
//			}
//		}
//
//		Object[] var5 = var1.pcall(var2, var3);// 76
//		return var5;// 77
//	}

//	public Object[] pcall(KahluaThread var1, Object var2, Object var3) {
//		if (var3 != null) {// 81
//			var3 = this.converterManager.fromJavaToLua(var3);// 82
//		}
//
//		Object[] var4 = var1.pcall(var2, new Object[]{var3});// 85
//		return var4;// 86
//	}

	public Boolean protectedCallBoolean(KahluaThread var1, Object var2, Object var3) {
		var3 = this.converterManager.fromJavaToLua(var3);// 90
		return var1.pcallBoolean(var2, var3);// 91
	}

	public Boolean protectedCallBoolean(KahluaThread var1, Object var2, Object var3, Object var4) {
		var3 = this.converterManager.fromJavaToLua(var3);// 95
		var4 = this.converterManager.fromJavaToLua(var4);// 96
		return var1.pcallBoolean(var2, var3, var4);// 97
	}

	public Boolean protectedCallBoolean(KahluaThread var1, Object var2, Object var3, Object var4, Object var5) {
		var3 = this.converterManager.fromJavaToLua(var3);// 101
		var4 = this.converterManager.fromJavaToLua(var4);// 102
		var5 = this.converterManager.fromJavaToLua(var5);// 103
		return var1.pcallBoolean(var2, var3, var4, var5);// 104
	}

	public Boolean pcallBoolean(KahluaThread var1, Object var2, Object[] var3) {
		if (var3 != null) {// 108
			for(int var4 = var3.length - 1; var4 >= 0; --var4) {// 109
				var3[var4] = this.converterManager.fromJavaToLua(var3[var4]);// 110
			}
		}

		return var1.pcallBoolean(var2, var3);// 113
	}

//	public LuaReturn protectedCall(KahluaThread var1, Object var2, Object... var3) {
//		return LuaReturn.createReturn(this.pcall(var1, var2, var3));// 117
//	}

//	public void protectedCallVoid(KahluaThread var1, Object var2, Object var3) {
//		var3 = this.converterManager.fromJavaToLua(var3);// 121
//		var1.pcallvoid(var2, var3);// 122
//	}// 123

	public void protectedCallVoid(KahluaThread var1, Object var2, Object var3, Object var4) {
		var3 = this.converterManager.fromJavaToLua(var3);// 126
		var4 = this.converterManager.fromJavaToLua(var4);// 127
		var1.pcallvoid(var2, var3, var4);// 128
	}// 129

	public void protectedCallVoid(KahluaThread var1, Object var2, Object var3, Object var4, Object var5) {
		var3 = this.converterManager.fromJavaToLua(var3);// 132
		var4 = this.converterManager.fromJavaToLua(var4);// 133
		var5 = this.converterManager.fromJavaToLua(var5);// 134
		var1.pcallvoid(var2, var3, var4, var5);// 135
	}// 136

	public void protectedCallVoid(KahluaThread var1, Object var2, Object[] var3) {
		this.pcallvoid(var1, var2, var3);// 139
	}// 140

	public Boolean protectedCallBoolean(KahluaThread var1, Object var2, Object[] var3) {
		return this.pcallBoolean(var1, var2, var3);// 143
	}
}
