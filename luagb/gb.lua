local Gameboy = require("gameboy/init")

if bit.Javabitlib then
  print("Java Bit lib")
end


local function play_gameboy_audio()
end


function readRomFromFile(romname)
  local rombase = "C:\\Users\\jym\\AppData\\Roaming\\LOVE\\LuaGB\\games\\";
  local card = {data={}, size=0}

  loadRom(card, rombase .. romname);

  function card:byte(i)
    return card.data[i]
  end
  return card
end

local romname1 = "Legend of Zelda, The - Link's Awakening (U) (V1.2) [!].gb"
local romname2 = "Tetris.gb"
--local card2 = require("cartridge/"+ romname1)
local card = readRomFromFile(romname1)



local gb = Gameboy.new({})
gb.cartridge.load(card, card.size)
gb:reset()
gb.audio.on_buffer_full(play_gameboy_audio)


gbrunning = 1
while (gbrunning) do
  for i = 0, 1000, 1 do
    gb:step()
  end

  local pixels = gb.graphics.game_screen
  setFrame(gb.graphics.vblank_count)
  updateScreen(pixels)
end