require "ISUI/ISModalDialog"
require "ISUI/ISLabel"
require "ISUI/ISButton"

local bit = require("bit")

GameboyEmu = {}
GameboyEmu.romMap = require("cartridge/gb-game-mapping")
GameboyEmu.isPlaying = false
print('GB loaded')

-- https://theindiestone.com/forums/index.php?/topic/9799-key-code-reference/
local keyW, keyS, keyA, keyD = 17, 31, 30, 32
local keyJ, keyK, keyH, keyF = 36, 37, 35, 33
local keyUP, keyDn, KeyLf, KeyRt = 200, 208, 203, 205
local keyEnt, keyCtrl, KeyEnd, KeyPgdn = 28, 157, 207, 209
local dia_w, dia_h = 500, 500

local keyMap = {
  { 'Left', KeyLf },
  { 'Right', KeyRt },
  { 'Up', keyUP },
  { 'Down', keyDn },
  
  { 'A', KeyEnd },
  { 'B', KeyPgdn },
  
  { 'Start', keyEnt },
  { 'Select', keyCtrl },
}


local function close(terget, button, p1, p2)
  print("GB closed")
  GameboyEmu.isPlaying = false
end


local function play_gameboy_audio(buffer)
end


local function checkKey(input) 
  local nu = false
  
  for i, k in pairs(keyMap) do
    local st = 0
    
    if isKeyPressed(k[2]) then
      st = 1
    end
    if st ~= input.keys[k[1]] then
      input.keys[k[1]] = st
      nu = true
      --print("set ", k[1])
    end
  end
  
  if nu then
    input.update()
  end
end


local function center(m) 
end


local function playGameBoy(romfile) 
  local diax = (getCore():getScreenWidth() - dia_w) / 2
  local diay = (getCore():getScreenHeight() - dia_h) / 2
  -- x, y, width, height, text, yesno, target, onclick, player, param1, param2
  local m = ISModalDialog:new(diax, diay, dia_w, dia_h, "Game Boy", false, player, close, nil, 0, 0)
  m:initialise()
  m:addToUIManager()
  m:setAlwaysOnTop(true)
  
  local card = require(romfile);
  
  if card == nil then
    local msg = "Cannot Play this game, \nyou dont have ROM " .. romfile
    print("ERROR rom: ", msg)
    -- x, y, width, height, title, clicktarget, onclick, onmousedown, allowMouseUpProcessing
    local cpr = ISButton:new(100,200,300, 150, msg, nil, function() m:close();close() end, nil, false)
    cpr:initialise()
    m:addChild(cpr)
    return
  end
  
  local Gameboy = require("gameboy/init")
  local gb = Gameboy.new({})
  gb.cartridge.load(card, card.size)
  gb:reset()
  gb.audio.on_buffer_full(play_gameboy_audio)
  
  local bg = getTexture("media/textures/gameboyscreen.png")
  local t = getTexture("media/textures/gb_pixel.png")
  local w, h = 160, 144
  local r, g, b = 0,0,0
  local x, y = 0,0
  local dx, dy = 0,0
  local offx = (500 - w*2) /2
  local offy = 100
  local c = nil
  local rd = getRenderer()
  local uiframe = 0
  local drawSplit = 1
  local drawPixel = 0
  
  m.render = function() 
    checkKey(gb.input)
    uiframe = uiframe + 1
    
    if uiframe % 20 == 0 then
      local s = gb.processor.save_state()
      local rs = gb.graphics.registers
      --print("PC: ", s.registers.pc, ' vbc:', gb.graphics.vblank_count, ' ui:', uiframe, " DP:", drawPixel)
    end
    drawPixel = 0
    
    -- rd:clearSprites()
    -- gb:step()
    -- gb:run_until_hblank()
    -- gb:run_until_vblank()
    
    for i = 0, 1000, 1 do 
      gb:step()
    end
      
    m:drawTexture(bg, offx, offy, 1, 1, 1, 1)
    gb.graphics.update()
    local pixels = gb.graphics.game_screen
    
    -- This is very slow function
    for i = uiframe % drawSplit, w*h-1, drawSplit do
      x = (i % w)
      y = math.floor(i / w)
      dx = x *2 + offx
      dy = y *2 + offy
      c = pixels[y][x]
      
      if c[1] ~= 255 or c[2] ~= 255 or c[3] ~= 255 then
        r = c[1] / 255
        g = c[2] / 255
        b = c[3] / 255
        m:drawTexture(t, dx, dy, 1, r, g, b)
        drawPixel = drawPixel + 1
        --t:render(dx, dy, 1, 1, r, g, b, 1, nil)
      end
    end
  end
  
  --local buf = t:getData() -- crash!
end


local function OnCanPerform(recipe, player, item)
  --if VGC_Scripts then
  --  if not VGC_Scripts.OnCanPerform.RequireElectricityToPerform(recipe, player, item) then
  --    return false
  --  end
  --end
  local romfile1 = "cartridge/Pokemon Red.gb"
  local romfile2 = "cartridge/Trump Boy (J).gb"
  local romfile3 = "cartridge/Super Mario Land (JUE) (V1.1) [!].gb"
  return true
end


local function OnTest()
  print("GB OnTest")
end


local function OnCreate(items, result, player)
  if GameboyEmu.isPlaying then return false end
  local rom = nil
  
  for i=0, items:size() - 1 do
    local item = items:get(i)
    print("GB OnCreate ", item:getDisplayName())
    rom = GameboyEmu.romMap[ item:getDisplayName() ]
    if rom then break end
  end
  
  if rom then
    print("Play GB ", rom)
    playGameBoy(rom)
    GameboyEmu.isPlaying = true
  end
end


local function OnGrab()
  print("GB OnGrab")
end


GameboyEmu.play = playGameBoy
GameboyEmu.OnCanPerform = OnCanPerform
GameboyEmu.OnTest = OnTest
GameboyEmu.OnCreate = OnCreate
GameboyEmu.OnGrab = OnGrab

playGameBoy()