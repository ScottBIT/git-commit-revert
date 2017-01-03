package CommitFeature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.NullOutputStream;

import ch.uzh.ifi.seal.changedistiller.model.classifiers.SignificanceLevel;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;

public class DiffCommit {

	private Git git;
	public List<String> subSysList;

	public DiffCommit(Git git) {
		this.git = git;
		subSysList = new ArrayList<String>();
	}

	public static void main(String[] args) {
		
	}

	// child commit, parent commit
	public List<DiffEntry> diffMethod(String Child, String Parent)
			throws GitAPIException, IncorrectObjectTypeException, IOException {

		Repository repository = git.getRepository();

		ObjectReader reader = repository.newObjectReader();
		CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();

		ObjectId old = repository.resolve(Child + "^{tree}");
		ObjectId head = repository.resolve(Parent + "^{tree}");
		oldTreeIter.reset(reader, old);
		CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
		newTreeIter.reset(reader, head);

		OutputStream outputStream = NullOutputStream.INSTANCE;
		DiffFormatter formatter = new DiffFormatter(outputStream);
		formatter.setRepository(git.getRepository());
		formatter.setDetectRenames(true);

		List<DiffEntry> diffs = formatter.scan(oldTreeIter, newTreeIter);

		return diffs;
	}

	public FileDiffEntry getFileDiffEntry(DiffEntry diffEntry)
			throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		FileDiffEntry thisFileDiffEntry = new FileDiffEntry();
		String oldPath = diffEntry.getOldPath();
		String newPath = diffEntry.getNewPath();
		ChangeType cType = diffEntry.getChangeType();
		String type = cType.name();
		String diffText = out.toString("UTF-8");

		thisFileDiffEntry.setNewPath(newPath);
		thisFileDiffEntry.setOldPath(oldPath);
		thisFileDiffEntry.setDiffText(diffText);
		thisFileDiffEntry.setType(type);

		System.out.println(diffText);
		out.reset();

		return thisFileDiffEntry;
	}

	public List<Commit> getFeaturedAllCommits(List<Commit> allCommits)
			throws IncorrectObjectTypeException, GitAPIException, IOException {
		JgitDiff dif = new JgitDiff(git);
		for (int i = allCommits.size() - 1; i > 0; i--) {
			Commit oldCommit = allCommits.get(i);
			Commit newCommit = allCommits.get(i - 1);
			
			int numberOfAddFiles = 0;
			int numberOfDeleteFiles = 0;
			int numberOfModifyFiles = 0;
			int numberOfRenameFiles = 0;
			int numberOfLineChanges = 0;
			int numberOfLow = 0;
			int numberOfMedium = 0;
			int numberOfHigh = 0;
			int numberOfCrucial = 0;

			String newCommitId = newCommit.getCommitid();
			String oldCommitId = oldCommit.getCommitid();
			
			List<DiffEntry> thisDiffs = diffMethod(oldCommitId, newCommitId);
			dif.getInfo(oldCommitId, newCommitId);
			int addlines = dif.getAddLines();
			int dellines = dif.getDelLines();
			newCommit.setAddlines(addlines);
			newCommit.setDellines(dellines);
			int subSys = dif.getSubSystemNum();
			newCommit.setSubSystemNum(subSys);

			for (DiffEntry diffEntry : thisDiffs) {
				FileDiffEntry thisFileDiffEntry = getFileDiffEntry(diffEntry);
				String thisType = thisFileDiffEntry.getType();
				if (thisType.equals("ADD")) {
					numberOfAddFiles = numberOfAddFiles + 1;
				} else if (thisType.equals("MODIFY")) {
					numberOfModifyFiles = numberOfModifyFiles + 1;
				} else if (thisType.equals("RENAME")) {
					numberOfRenameFiles = numberOfRenameFiles + 1;
				} else if (thisType.equals("DELETE")) {
					numberOfDeleteFiles = numberOfDeleteFiles + 1;
				} else {
				}

				String oldFilePath = thisFileDiffEntry.getOldPath();
				String newFilePath = thisFileDiffEntry.getNewPath();

				if (oldFilePath.indexOf(".java") > -1
						&& newFilePath.indexOf(".java") > -1) {

					CheckOutFile checker = new CheckOutFile(git);

					checker.CheckOutFileByCommitId(oldCommitId, oldFilePath);
					String oldCopyPath = gitLogAnalysis.outputHome + "oldcopy"
							+ oldFilePath.replaceAll("/", "_");
					checker.Copy(gitLogAnalysis.projectHome + oldFilePath,
							oldCopyPath);

					checker.CheckOutFileByCommitId(newCommitId, newFilePath);
					String newCopyPath = gitLogAnalysis.outputHome + "newcopy"
							+ newFilePath.replaceAll("/", "_");
					;
					checker.Copy(gitLogAnalysis.projectHome + newFilePath,
							newCopyPath);

					// Fine-grained features
					ChangeDistillerClass changeDistiller = new ChangeDistillerClass();

					List<SourceCodeChange> changes = changeDistiller
							.getFineGrainedChange(oldCopyPath, newCopyPath);

					if (changes != null) {

						for (SourceCodeChange change : changes) {
							numberOfLineChanges = numberOfLineChanges + 1;
							SignificanceLevel thisLevel = change
									.getSignificanceLevel();
							String thisLevelString = thisLevel.toString();
							if (thisLevelString.equals("LOW")) {
								numberOfLow = numberOfLow + 1;
							} else if (thisLevelString.equals("MEDIUM")) {
								numberOfMedium = numberOfMedium + 1;
							} else if (thisLevelString.equals("HIGH")) {
								numberOfHigh = numberOfHigh + 1;
							} else if (thisLevelString.equals("CRUCIAL")) {
								numberOfCrucial = numberOfCrucial + 1;
							} else {
							}

						}
					}
					 checker.deleteFile(oldCopyPath);
					 checker.deleteFile(newCopyPath);
				}				
			}
			newCommit.setAddFiles(numberOfAddFiles);
			newCommit.setDeleteFiles(numberOfDeleteFiles);
			newCommit.setRenameFiles(numberOfRenameFiles);
			newCommit.setModifyFiles(numberOfModifyFiles);

			newCommit.setNumberOfLow(numberOfLow);
			newCommit.setNumberOfMedium(numberOfMedium);
			newCommit.setNumberOfHigh(numberOfHigh);
			newCommit.setNumberOfCrucial(numberOfCrucial);

		}
		return allCommits;
	}

}
