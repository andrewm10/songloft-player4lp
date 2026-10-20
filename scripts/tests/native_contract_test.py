"""Exercise the native contract script with isolated repository fixtures."""

import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class NativeContractTests(unittest.TestCase):
    def test_release_rules_change_dart_contract_without_changing_go(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'scripts').mkdir()
            shutil.copyfile(
                Path(__file__).resolve().parents[1] / 'compute_native_contract.sh',
                root / 'scripts/compute_native_contract.sh',
            )
            source = root / 'android/app/src/main/kotlin/com/songloft/songloft_flutter'
            source.mkdir(parents=True)
            (source / 'Bridge.kt').write_text(
                'val channel = "com.songloft/test"\n'
                'if (call.method == "test") {}\n"test" -> Unit\n'
            )
            (root / 'exports.txt').write_text('func GetPort() int\n')

            def contract():
                return json.loads(subprocess.check_output(
                    ['bash', str(root / 'scripts/compute_native_contract.sh'),
                     '--export-surface', str(root / 'exports.txt')],
                    text=True,
                ))

            original = contract()
            rules = root / 'android/app/proguard-rules.pro'
            rules.write_text('-keep interface kotlin.coroutines.Continuation { *; }\n')
            changed = contract()
            self.assertNotEqual(original['dart'], changed['dart'])
            self.assertEqual(original['go'], changed['go'])
            rules.write_text('# Documentation only\n' + rules.read_text() + '\n')
            self.assertEqual(changed, contract())


if __name__ == '__main__':
    unittest.main()
