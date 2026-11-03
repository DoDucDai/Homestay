"""Run a minimal live OpenRouter check without printing credentials."""
import argparse
import importlib.util
from pathlib import Path

parser=argparse.ArgumentParser(description='Check the configured free Mochi model')
parser.add_argument('--model', default=None)
args=parser.parse_args()
spec=importlib.util.spec_from_file_location('mochi_service',Path(__file__).parent/'app/server.py')
service=importlib.util.module_from_spec(spec)
spec.loader.exec_module(service)
if args.model:
    service.MODEL=args.model
print('Model:',service.MODEL)
try:
    reply=service.answer('Hãy hướng dẫn cách kiểm tra phòng homestay còn trống.',[],None)
    print('OpenRouter OK\n'+reply)
except service.Problem as error:
    print(error.message)
    underlying=error.__cause__ or error.__context__
    reason=getattr(underlying,'reason',underlying)
    code=getattr(reason,'winerror',getattr(underlying,'code',None))
    if code:print('Connection/status code:',code)
    raise SystemExit(1)
