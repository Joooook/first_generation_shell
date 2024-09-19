import hashlib
import os.path
import shutil
import sys
import zlib
import zipfile


def process_apk_data(apk_data:bytes):
    """
    用于处理apk数据，比如加密，压缩等，都可以放在这里。
    :param apk_data:
    :return:
    """
    return apk_data

# 使用前需要修改的部分
keystore_path='demo1.keystore'
keystore_password='123456'
src_apk_file_path= '/Users/zhou39512/AndroidStudioProjects/MyApplication/app/build/outputs/apk/debug/app-debug.apk'
shell_apk_file_path= '/Users/zhou39512/AndroidStudioProjects/Unshell/app/build/outputs/apk/debug/app-debug.apk'
buildtools_path='~/Library/Android/sdk/build-tools/34.0.0/'

# 承载apk的文件名
carrier_file_name= 'classes.dex'
# 中间文件夹
intermediate_dir= 'intermediates'
intermediate_apk_name='app-debug.apk'
intermediate_aligned_apk_name='app-debug-aligned.apk'
intermediate_apk_path=os.path.join(intermediate_dir,intermediate_apk_name)
intermediate_carrier_path=os.path.join(intermediate_dir, carrier_file_name)
intermediate_aligned_apk_path=os.path.join(intermediate_dir,intermediate_aligned_apk_name)
if os.path.exists(intermediate_dir):
    shutil.rmtree(intermediate_dir)
os.mkdir(intermediate_dir)

# 解压apk
shell_apk_file=zipfile.ZipFile(shell_apk_file_path)
shell_apk_file.extract(carrier_file_name,intermediate_dir)

# 查找dex
if not os.path.exists(os.path.join(intermediate_dir, carrier_file_name)):
    raise FileNotFoundError(f'{carrier_file_name} not found')

src_dex_file_path= os.path.join(intermediate_dir, carrier_file_name)

#读取
src_apk_file=open(src_apk_file_path, 'rb')
src_dex_file=open(src_dex_file_path, 'rb')

src_apk_data=src_apk_file.read()
src_dex_data=src_dex_file.read()

# 处理apk数据
processed_apk_data=process_apk_data(src_apk_data)
processed_apk_size=len(processed_apk_data)

# 构建新dex数据
new_dex_data=src_dex_data+processed_apk_data+int.to_bytes(processed_apk_size,8,'little')

# 更新文件大小
file_size=len(processed_apk_data)+len(src_dex_data)+8
new_dex_data=new_dex_data[:32]+int.to_bytes(file_size,4,'little')+new_dex_data[36:]

# 更新sha1摘要
signature=hashlib.sha1().digest()
new_dex_data=new_dex_data[:12]+signature+new_dex_data[32:]

# 更新checksum
checksum=zlib.adler32(new_dex_data[12:])
new_dex_data=new_dex_data[:8]+int.to_bytes(checksum,4,'little')+new_dex_data[12:]

# 写入新dex
intermediate_carrier_file= open(intermediate_carrier_path, 'wb')
intermediate_carrier_file.write(new_dex_data)
intermediate_carrier_file.close()
src_apk_file.close()
src_dex_file.close()

# 添加环境变量，为重打包做准备
os.environ.update({'PATH':os.environ.get('PATH')+f':{buildtools_path}'})
# 重打包
r=os.popen(f"cp {shell_apk_file_path} {intermediate_apk_path}").read()
print(r)
os.chdir(intermediate_dir)
r=os.popen(f'zip {intermediate_apk_name} {carrier_file_name}').read()
os.chdir('../')
print(r)
# 对齐
r=os.popen(f'zipalign 4 {intermediate_apk_path} {intermediate_aligned_apk_path}').read()
print(r)
# 签名
r=os.popen(f'apksigner sign -ks {keystore_path} --ks-pass pass:{keystore_password} {intermediate_aligned_apk_path}').read()
print(r)
r=os.popen(f'cp {intermediate_aligned_apk_path} ./app-out.apk').read()
print(r)
print('Success')