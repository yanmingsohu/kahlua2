
-- http://bitop.luajit.org/api.html

testAssert("hej" == "hej")

function eq(a, b)
  assert(a == b, a .. " != " .. b)
end

assert(bit, "bit object")
assert(bit.tobit, "tobit function")

assert(bit.tobit(0xffffffff) == -1, "bad tobit " .. bit.tobit(0xffffffff))
assert(bit.tobit(0xffffffff + 1) == 0)
assert(bit.tobit(2^40 + 1234) == 1234)

assert(bit.tohex(1) == '00000001', "need zero " .. bit.tohex(1))
assert(bit.tohex(-1) == 'ffffffff')
assert(bit.tohex(0xffffffff) == 'ffffffff')
assert(bit.tohex(-1, -8) == 'FFFFFFFF', bit.tohex(-1, -8) .. "!=" .. 'FFFFFFFF')
assert(bit.tohex(0x21, 4) == '0021', bit.tohex(0x21, 4) .. '!=' .. '0021')
assert(bit.tohex(0x87654321, 4) == '4321')

assert(bit.bnot(0) == -1)
assert(bit.tohex(bit.bnot(0)) == 'ffffffff', bit.tohex(bit.bnot(0)))
assert(bit.bnot(-1) == 0)
assert(bit.bnot(0xffffffff) == 0)
assert(bit.tohex(bit.bnot(0x12345678)) == 'edcba987', bit.bnot(0x12345678))

assert(bit.bor(1, 2, 4, 8) == 15)
assert(bit.tohex(bit.band(0x12345678, 0xff)) == '00000078')
assert(bit.tohex(bit.bxor(0xa5a5f0f0, 0xaa55ff00)) == '0ff00ff0')

eq(bit.lshift(1, 0), 1)
eq(bit.lshift(1, 8), 256)
eq(bit.lshift(1, 40), 256)
eq(bit.rshift(256, 8), 1)
eq(bit.rshift(-256, 8), 16777215)
eq(bit.arshift(256, 8), 1)
eq(bit.arshift(-256, 8), -1)
eq(bit.tohex(bit.lshift(0x87654321, 12)), '54321000')
eq(bit.tohex(bit.rshift(0x87654321, 12)), '00087654')
eq(bit.tohex(bit.arshift(0x87654321, 12)), 'fff87654')

eq(bit.tohex(bit.rol(0x12345678, 12)), '45678123')
eq(bit.tohex(bit.ror(0x12345678, 12)), '67812345')

eq(bit.tohex(bit.bswap(0x12345678)), '78563412')
eq(bit.tohex(bit.bswap(0x78563412)), '12345678')