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


import se.krka.kahlua.vm.Coroutine;
import se.krka.kahlua.vm.LuaCallFrame;
import se.krka.kahlua.vm.LuaClosure;
import se.krka.kahlua.vm.Prototype;


public interface IConst {
  int rootClosure = 0;
  int vThis = 0; // must be 0
  int vCallframe = 1;
  int vPlatform = 2;
  int vClosure = 3;
  int vPrototype = 4;
  int vCI = 5;

  int vUser = 6;

  Class O = Object.class;
  Class S = String.class;
  Class I = int.class;
  Class F = float.class;
  Class D = double.class;
  Class B = boolean.class;
  Class PT = Prototype.class;
  Class FR = LuaCallFrame.class;
  Class CU = LuaClosure.class;
  Class LS = LuaScript.class;
  Class CR = Coroutine.class;
  Class CI = ClosureInf.class;

  String TS = "TRUE";
  String FS = "FALSE";
  String CONSTRUCTOR = "<init>";
}