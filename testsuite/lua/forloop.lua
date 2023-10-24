local assert = assert
local pairs = pairs
local setmetatable = setmetatable
local type = type

local mt = {}
function mt.__index(t, k)
	if type(k) == "string" then
		return k .. k
	end
end

local t = setmetatable({}, mt)
testCall(function()
	for key, value in pairs{1} do
		assert(true)
		assert(t.hello == "hellohello")
	end
end)

local arr = {};
for i = 0, 10, 1 do
	arr[i] = i+10
	--print("for -> ", i)
end

local x = 0
local function test_for()
	assert(arr[x] == 10+x, "for item ".. x)
	--print(x, arr[x], x+10)
	x = x+1
end

test_for() -- 0

test_for() -- 1
test_for()
test_for()
test_for()
test_for()

test_for()
test_for()
test_for()
test_for()
test_for() -- 10