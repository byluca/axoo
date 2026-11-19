import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import upload_memes


class UploadMemesTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.folder = Path(temporary.name)
        (self.folder / "foto.JPG").write_bytes(b"photo")
        (self.folder / "animazione.gif").write_bytes(b"gif")
        (self.folder / "note.txt").write_text("ignore")
        (self.folder / "subfolder").mkdir()
        self.output = io.StringIO()
        redirect = contextlib.redirect_stdout(self.output)
        redirect.__enter__()
        self.addCleanup(redirect.__exit__, None, None, None)

    def test_preview_does_not_upload_or_require_credentials(self):
        with patch.object(upload_memes.storage, "SUPABASE_URL", ""), patch.object(upload_memes.storage, "SERVICE_KEY", ""), patch.object(upload_memes.storage, "upload") as upload:
            self.assertEqual(upload_memes.upload_folder(self.folder, "italia"), 2)
        upload.assert_not_called()
        self.assertIn("italia/foto.JPG", self.output.getvalue())

    def test_apply_uploads_only_images_with_correct_mime_and_cache(self):
        with patch.object(upload_memes.storage, "SUPABASE_URL", "https://example.test"), patch.object(upload_memes.storage, "SERVICE_KEY", "test"), patch.object(upload_memes.storage, "upload") as upload:
            self.assertEqual(upload_memes.upload_folder(self.folder, "world", apply=True), 2)
        self.assertEqual(upload.call_count, 2)
        upload.assert_any_call("world/foto.JPG", b"photo", "image/jpeg", "604800")
        upload.assert_any_call("world/animazione.gif", b"gif", "image/gif", "604800")

    def test_apply_without_configuration_makes_no_upload(self):
        with patch.object(upload_memes.storage, "SUPABASE_URL", ""), patch.object(upload_memes.storage, "upload") as upload:
            with self.assertRaises(ValueError):
                upload_memes.upload_folder(self.folder, "tv", apply=True)
        upload.assert_not_called()

    def test_invalid_folder_or_category_is_rejected(self):
        for folder, category in [(self.folder / "missing", "sport"), (self.folder, "unknown"), (self.folder / "subfolder", "musica")]:
            with self.subTest(folder=folder, category=category), self.assertRaises(ValueError):
                upload_memes.upload_folder(folder, category)


if __name__ == "__main__":
    unittest.main()
