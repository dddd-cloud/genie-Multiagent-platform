import { useNavigate } from 'react-router-dom';
import MarketplacePage from '@/features/marketplace/MarketplacePage';
import type { MarketplaceDraftResponse } from '@/services/marketplace';
import {
  mapDraftToTeamForm,
  marketplaceDraftTarget,
} from './draftMapping';

export default function MarketplaceMount() {
  const navigate = useNavigate();

  function handleDraftCreated(result: MarketplaceDraftResponse) {
    const target = marketplaceDraftTarget(result);
    if (target === 'TEAM') {
      navigate('/app/teams/new', {
        state: { draft: mapDraftToTeamForm(result.draft) },
      });
    }
  }

  return <MarketplacePage onDraftCreated={handleDraftCreated} />;
}
