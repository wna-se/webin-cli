package uk.ac.ebi.ena.webin.cli.context.transcriptome;

import org.junit.Test;
import uk.ac.ebi.ena.webin.cli.validator.manifest.TranscriptomeManifest;

import static uk.ac.ebi.ena.webin.cli.manifest.ManifestReaderFileSuffixTester.valid;
import static uk.ac.ebi.ena.webin.cli.manifest.ManifestReaderFileSuffixTester.invalid;

public class TranscriptomeManifestReaderFileSuffixTest {

  @Test
  public void testValidFileSuffix() {
    Class<TranscriptomeManifestReader> manifestReader = TranscriptomeManifestReader.class;
    valid(manifestReader, TranscriptomeManifest.FileType.FASTA, ".fasta.gz");
    valid(manifestReader, TranscriptomeManifest.FileType.FLATFILE, ".txt.gz");
  }

  @Test
  public void testInvalidFileSuffix() {
    Class<TranscriptomeManifestReader> manifestReader = TranscriptomeManifestReader.class;
    // Invalid suffix before .gz
    invalid(manifestReader, TranscriptomeManifest.FileType.FASTA, ".INVALID.gz");
    // No .gz
    invalid(manifestReader, TranscriptomeManifest.FileType.FASTA, ".fasta");
    invalid(manifestReader, TranscriptomeManifest.FileType.FLATFILE, ".txt");
  }

}
