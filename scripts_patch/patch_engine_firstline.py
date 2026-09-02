# -*- coding: utf-8 -*-
import io, re
f = r'C:\Users\wangk\Desktop\LinuxAIRuntime\runtime\browser\src\main\java\top\wkbin\taixu\runtime\browser\engine\AndroidInAppBrowserEngine.kt'
t = io.open(f, encoding='utf-8').read().replace('\r\n', '\n')

lines = t.split('\n')
print("BEFORE first line:", repr(lines[0][:120]))

# 第一行以 'OK:' 垃圾开头，剥离到 'package' 为止
lines[0] = lines[0][lines[0].rfind('package'):] if 'package' in lines[0] else lines[0]
t = '\n'.join(lines)

# 清理行内残留的 \r
t = t.replace('\r', '')

io.open(f, 'w', encoding='utf-8', newline='\n').write(t)
print("AFTER first line:", repr(lines[0]))
print("DONE")
