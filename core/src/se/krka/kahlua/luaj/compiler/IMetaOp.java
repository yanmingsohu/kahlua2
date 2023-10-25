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

package se.krka.kahlua.luaj.compiler;

public interface IMetaOp {

  int __add = -1;
  int __sub = -2;
  int __mul = -3;
  int __div = -4;
  int __mod = -5;
  int __pow = -6;
  int __unm = -7;
  int __concat = -15;
  int __len = -16;
  int __eq = -17;
  int __lt = -18;
  int __le = -19;
  int __index = -20;
  int __newindex = -21;
  int __call = -22;

  int __idiv = -8;
  int __band = -9;
  int __bor = -10;
  int __bxor = -11;
  int __bnot = -12;
  int __shl = -13;
  int __shr = -14;

  ILuaValue op(ILuaValue a, ILuaValue b);
}
