const fs = require('fs');
const path = require('path');

const __romFile = "Trump Boy (J).gb";
const baseDir = './';
const gb_game = '.gb';

fs.readdirSync(baseDir).forEach(f=>{
  if (path.extname(f) == gb_game) {
    convFile(baseDir + f);
  }
});


function convFile(romFile) {
  let luaFile = romFile+".lua";
  
  if (fs.existsSync(luaFile)) {
    console.log("EXISTS.", romFile);
    return;
  } else {
    console.log("COVNVER", romFile);
  }
  
  let data = fs.readFileSync(romFile);

  let buf = ['local r = {}', '\n'];
  buf.push('\nr.data =', '{');
  for (let i=0; i<data.length; ++i) {
    buf.push(data[i], ',');
  }
  buf.push('}');
  buf.push('\nr.size =', data.length);
  buf.push('\nfunction r:byte(i)\n  return\n  r.data[i]\nend');
  buf.push('\nreturn r');

  fs.writeFileSync(luaFile, buf.join(''));
}