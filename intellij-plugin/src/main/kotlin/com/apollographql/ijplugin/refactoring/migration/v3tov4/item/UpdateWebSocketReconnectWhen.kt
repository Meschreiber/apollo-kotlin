package com.apollographql.ijplugin.refactoring.migration.v3tov4.item

import com.apollographql.ijplugin.refactoring.findMethodReferences
import com.apollographql.ijplugin.refactoring.migration.apollo3
import com.apollographql.ijplugin.refactoring.migration.item.MigrationItem
import com.apollographql.ijplugin.refactoring.migration.item.MigrationItemUsageInfo
import com.apollographql.ijplugin.refactoring.migration.item.toMigrationItemUsageInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory

object UpdateWebSocketReconnectWhen : MigrationItem() {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    return findMethodReferences(
        project = project,
        className = "$apollo3.ApolloClient.Builder",
        methodName = "webSocketReconnectWhen",
    )
        .toMigrationItemUsageInfo()
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: MigrationItemUsageInfo) {
    val element = usage.element
    val methodCall = element.parentOfType<KtCallExpression>() ?: return
    val lambdaArgument = methodCall.valueArguments.firstOrNull() as? KtLambdaArgument ?: return
    val lambdaExpression = lambdaArgument.getLambdaExpression() ?: return
    val lambdaParameters = lambdaExpression.functionLiteral.valueParameterList
    val psiFactory = KtPsiFactory(project)
    element.replace(psiFactory.createExpression("webSocketReopenWhen"))
    if (lambdaParameters == null) {
      // { ... } -> { it, _ -> ...}
      val newLambdaExpression = psiFactory.createLambdaExpression("it, _", lambdaExpression.bodyExpression!!.text)
      lambdaExpression.replace(newLambdaExpression)
    } else {
      // { a -> ...} -> { a, _ -> ...}
      lambdaParameters.addParameter(psiFactory.createParameter("_"))
    }
  }
}
