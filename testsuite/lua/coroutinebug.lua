if NewThreadVersion then print("NewThreadVersion cannot support"); return end

local coro = coroutine.create(function()
	error"foo"
end)
coroutine.resume(coro)

