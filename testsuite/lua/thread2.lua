---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by jym.
--- DateTime: 2023/10/22 10:27
---

function x2(a,b)
  return a+b
end

function x(i)
  return x2(i,1)
end

local a1 = 101
a2 = 1

a3 = a1 + a2 + 1
a4 = x(a3)
return a1,a2,a3,a4