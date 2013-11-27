package org.intellij.plugins.ceylon.action;

import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import java.util.Properties;
import java.util.regex.Pattern;

// todo: check if module already exists (use ModuleIndex)
public class CeylonAddModule extends AnAction {

    public CeylonAddModule() {
        super(IconLoader.getIcon("/icons/ceylon.png"));
    }

    public void actionPerformed(final AnActionEvent e) {
        final Pattern pattern = Pattern.compile("([a-z_A-Z]|\\\\[iI])[a-z_A-Z0-9]*(\\.([a-z_A-Z]|\\\\[iI])[a-z_A-Z0-9]*)*");
        final Project project = e.getProject();

        if (project == null) {
            return;
        }

        // todo: choose module version in the same dialog
        final String moduleName = Messages.showInputDialog(project, "Enter module name", "Add Ceylon Module", null, null, new InputValidatorEx() {
            @Override
            public boolean checkInput(String name) {
                return pattern.matcher(name).matches() && !"default".equals(name)
//                        && ModuleIndex.getInstance().get(name, project, GlobalSearchScope.projectScope(project)).isEmpty()
                        ;
            }

            @Override
            public boolean canClose(String inputString) {
                return checkInput(inputString);
            }

            @Nullable
            @Override
            public String getErrorText(String name) {
                if (!pattern.matcher(name).matches()) {
                    return String.format("\"%s\" is not a valid name for a module.", name);
                } else if ("default".equals(name)) {
                    return "\"default\" is a reserved module name.";
//                } else if (!ModuleIndex.getInstance().get(name, project, GlobalSearchScope.projectScope(project)).isEmpty()) {
//                    return String.format("Module %s already exists.", name);
                }
                return null;
            }
        });

        VirtualFile eventSourceFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);

        if (moduleName == null || eventSourceFile == null) {
            return;
        }

        while (eventSourceFile != null && !(eventSourceFile instanceof VirtualDirectoryImpl)) {
            eventSourceFile = eventSourceFile.getParent();
        }
        if (eventSourceFile != null) {
            final VirtualDirectoryImpl eventDir = (VirtualDirectoryImpl) eventSourceFile;
            final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
            final VirtualFile srcRoot = projectRootManager.getFileIndex().getSourceRootForFile(eventDir);

            assert srcRoot != null;
            String eventPath = eventDir.getPath();
            final String srcRootPath = srcRoot.getPath();
            assert eventPath.startsWith(srcRootPath) : eventPath + " not in " + srcRootPath;

            // Make eventPath relative
            eventPath = eventPath.length() <= srcRootPath.length() ? "" : eventPath.substring(srcRootPath.length() + 1);

            final String eventPackage = eventPath.replace('/', '.');

            final PsiDirectory eventPsiDir = PsiManager.getInstance(project).findDirectory(eventDir);

            if (eventPsiDir != null) {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override public void run() {
                        FileTemplateManager templateManager = FileTemplateManager.getInstance();
                        PsiDirectory subdirectory = DirectoryUtil.createSubdirectories(moduleName, eventPsiDir, ".");
                        Properties variables = new Properties();
                        String fullModuleName = (eventPackage.isEmpty() ? "" : eventPackage + ".") + moduleName;
                        variables.put("MODULE_NAME", fullModuleName);
                        variables.put("MODULE_VERSION", "1.0.0");

                        try {
                            FileTemplateUtil.createFromTemplate(templateManager.getInternalTemplate("module.ceylon"), "module.ceylon", variables, subdirectory);
                            FileTemplateUtil.createFromTemplate(templateManager.getInternalTemplate("package.ceylon"), "package.ceylon", variables, subdirectory);
                            FileTemplateUtil.createFromTemplate(templateManager.getInternalTemplate("run.ceylon"), "run.ceylon", variables, subdirectory);
                        } catch (Exception e1) {
                            Logger.getInstance(CeylonAddModule.class).error("Can't create file from template", e1);
                        }
                    }
                });
            }
        }
    }


}
