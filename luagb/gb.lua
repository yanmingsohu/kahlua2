local romfile = "cartridge/Legend of Zelda, The - Link's Awakening (U) (V1.2) [!].gb"
local card = require(romfile);
local Gameboy = require("gameboy/init")

if bit.Javabitlib then
  print("Java Bit lib")
end


local function play_gameboy_audio()
end


local gb = Gameboy.new({})
gb.cartridge.load(card, card.size)
gb:reset()
gb.audio.on_buffer_full(play_gameboy_audio)


while (true) do
  for i = 0, 1000, 1 do
    --gb:step()
  end
--
--  local pixels = gb.graphics.game_screen
--  setFrame(gb.graphics.vblank_count)
--  updateScreen(pixels)
end