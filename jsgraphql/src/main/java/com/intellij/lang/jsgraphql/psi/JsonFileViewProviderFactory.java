package com.intellij.lang.jsgraphql.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.FileViewProviderFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class JsonFileViewProviderFactory implements FileViewProviderFactory {
  @Override
  public @NotNull FileViewProvider createFileViewProvider(@NotNull VirtualFile file,
                                                          Language language,
                                                          @NotNull PsiManager manager,
                                                          boolean eventSystemEnabled) {
    if (!SingleRootFileViewProvider.isTooLargeForContentLoading(file)) {
      SingleRootFileViewProvider.doNotCheckFileSizeLimit(file);
    }
    return new SingleRootFileViewProvider(manager, file, eventSystemEnabled);
  }
}
