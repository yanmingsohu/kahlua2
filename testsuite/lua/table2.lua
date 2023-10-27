---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by jym.
--- DateTime: 2023/10/24 15:19
---

local Graphics = {}

function Graphics.new(modules)
  local graphics = {}

  graphics.oam = {}

  graphics.initialize = function()
    graphics.reset()
  end

  graphics.reset = function()
    ---- zero out all of OAM
    for i = 0xFE00, 0xFE9F do
      graphics.oam[i] = 0
    end
    --print(graphics, graphics.oam)
    --print(graphics.oam[0xFE00])
  end

  return graphics
end


local g = Graphics.new()
g.initialize()


local t2 = {}
t2[2.0] = 2
t2["2"] = 3

assert(t2[2.0] == 2)
assert(t2[2] == 2)
assert(t2["2"] == 3)

t2[2] = 4

assert(t2[2.0] == 4)
assert(t2[2] == 4)
assert(t2["2"] == 3)